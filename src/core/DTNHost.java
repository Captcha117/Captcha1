/* 
 * Copyright 2010 Aalto University, ComNet
 * Released under GPLv3. See LICENSE.txt for details. 
 */
package core;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import movement.MovementModel;
import movement.Path;
import routing.MessageRouter;
import routing.util.RoutingInfo;

/**
 * A DTN capable host.
 */
public class DTNHost implements Comparable<DTNHost>
{
	private static int nextAddress = 0;
	private int address;

	private Coord location; // where is the host
	private Coord destination; // where is it going
	private Coord tempDestination;
	private Coord startLoction;
	private Coord lastLocation;

	private MessageRouter router;
	private MovementModel movement;
	private Path path;
	private double speed;
	private double nextTimeToMove;
	private String name;
	private List<MessageListener> msgListeners;
	private List<MovementListener> movListeners;
	private List<NetworkInterface> net;
	private ModuleCommunicationBus comBus;
	private int type; // the host type:pv or rsu or request

	public static final int TYPE_PV = 0;
	public static final int TYPE_RSU = 1;
	public static final int TYPE_REQUEST = 2;

	///////////
	public static final Double DETOUR_RATIO = 0.5; // 绕行阈值
	public static final Double WAIT_RATIO = 0.5; // 等待阈值

	private List<DTNHost> pvList = null;

	private Map<DTNHost, Double> costMap = null;
	private List<List<Double>> extraCost = null;
	private int flag = 0;
	private Double load = 0.0;

	private List<Integer> pickList = null; // pick列表
	private List<Integer> toList = null; // to列表
	private List<DTNHost> requestList = null; // 接受的请求列表
	private List<Message> messageList = null; // 消息列表
	private List<Integer> serviceState = null; // 服务状态列表
	private List<Double> waitList = null; // 等待列表
	private List<Double> detourList = null; // 绕行距离列表
	private List<Message> serviceOrder = null; // 服务顺序列表
	private int campacity; // PV最大容量
	private int acceptNumber; // 接受但未上车的请求数
	private int inServiceNumber; // 已上车的请求数
	private List<Double> goDistance = null; // 已行驶的距离列表
	private List<Double> odList = null; // od列表

	static
	{
		DTNSim.registerForReset(DTNHost.class.getCanonicalName());
		reset();
	}

	/**
	 * Creates a new DTNHost.
	 * 
	 * @param msgLs
	 *            Message listeners
	 * @param movLs
	 *            Movement listeners
	 * @param groupId
	 *            GroupID of this host
	 * @param interf
	 *            List of NetworkInterfaces for the class
	 * @param comBus
	 *            Module communication bus object
	 * @param mmProto
	 *            Prototype of the movement model of this host
	 * @param mRouterProto
	 *            Prototype of the message router of this host
	 */
	public DTNHost(List<MessageListener> msgLs, List<MovementListener> movLs, String groupId, List<NetworkInterface> interf, ModuleCommunicationBus comBus,
			MovementModel mmProto, MessageRouter mRouterProto)
	{
		this.comBus = comBus;
		this.location = new Coord(0, 0);
		this.address = getNextAddress();
		this.name = groupId + address;
		this.net = new ArrayList<NetworkInterface>();
		this.flag = 0;
		this.load = 0.0;
		if (groupId.equals("pv"))
		{
			this.type = TYPE_PV;
			this.extraCost = new ArrayList<List<Double>>();
			this.campacity = 10;
			this.acceptNumber = 0;
			this.inServiceNumber = 0;
			this.requestList = new ArrayList<DTNHost>();
			this.messageList = new ArrayList<Message>();
			this.serviceState = new ArrayList<Integer>();
			this.pickList = new ArrayList<Integer>();
			this.toList = new ArrayList<Integer>();
			this.waitList = new ArrayList<Double>();
			this.detourList = new ArrayList<Double>();
			this.serviceOrder = new ArrayList<Message>();
			this.goDistance = new ArrayList<Double>();
			this.odList = new ArrayList<Double>();
		}
		else if (groupId.equals("rsu"))
		{
			this.type = TYPE_RSU;
			this.costMap = new HashMap<DTNHost, Double>();

			this.pvList = new ArrayList<DTNHost>();
		}
		else
		{
			this.type = TYPE_REQUEST;
		}

		for (NetworkInterface i : interf)
		{
			NetworkInterface ni = i.replicate();
			ni.setHost(this);
			net.add(ni);
		}

		// TODO - think about the names of the interfaces and the nodes

		this.msgListeners = msgLs;
		this.movListeners = movLs;

		// create instances by replicating the prototypes
		this.movement = mmProto.replicate();
		this.movement.setComBus(comBus);
		this.movement.setHost(this);
		setRouter(mRouterProto.replicate());

		this.location = movement.getInitialLocation();
		// 将detination初始化为location
		this.destination = this.location;
		this.tempDestination = this.location;
		this.startLoction = this.location;
		this.lastLocation = this.location;

		this.nextTimeToMove = movement.nextPathAvailable();
		this.path = null;

		if (movLs != null)
		{ // inform movement listeners about the location
			for (MovementListener l : movLs)
			{
				l.initialLocation(this, this.location);
			}
		}
	}

	/**
	 * Returns a new network interface address and increments the address for subsequent calls.
	 * 
	 * @return The next address.
	 */
	private synchronized static int getNextAddress()
	{
		return nextAddress++;
	}

	/**
	 * Reset the host and its interfaces
	 */
	public static void reset()
	{
		nextAddress = 0;
	}

	/**
	 * Returns true if this node is actively moving (false if not)
	 * 
	 * @return true if this node is actively moving (false if not)
	 */
	public boolean isMovementActive()
	{
		return this.movement.isActive();
	}

	/**
	 * Returns true if this node's radio is active (false if not)
	 * 
	 * @return true if this node's radio is active (false if not)
	 */
	public boolean isRadioActive()
	{
		/* TODO: make this work for multiple interfaces */
		return this.getInterface(1).isActive();
	}

	/**
	 * Set a router for this host
	 * 
	 * @param router
	 *            The router to set
	 */
	private void setRouter(MessageRouter router)
	{
		router.init(this, msgListeners);
		this.router = router;
	}

	/**
	 * Returns the router of this host
	 * 
	 * @return the router of this host
	 */
	public MessageRouter getRouter()
	{
		return this.router;
	}

	/**
	 * Returns the network-layer address of this host.
	 */
	public int getAddress()
	{
		return this.address;
	}

	/**
	 * Returns this hosts's ModuleCommunicationBus
	 * 
	 * @return this hosts's ModuleCommunicationBus
	 */
	public ModuleCommunicationBus getComBus()
	{
		return this.comBus;
	}

	/**
	 * Informs the router of this host about state change in a connection object.
	 * 
	 * @param con
	 *            The connection object whose state changed
	 */
	public void connectionUp(Connection con)
	{
		// System.out.println("ConnectionUp:" + con.toString());
		this.router.changedConnection(con);
	}

	public void connectionDown(Connection con)
	{
		// System.out.println("ConnectionDown:" + con.toString());
		this.router.changedConnection(con);
	}

	/**
	 * Returns a copy of the list of connections this host has with other hosts
	 * 
	 * @return a copy of the list of connections this host has with other hosts
	 */
	public List<Connection> getConnections()
	{
		List<Connection> lc = new ArrayList<Connection>();

		for (NetworkInterface i : net)
		{
			lc.addAll(i.getConnections());
		}

		return lc;
	}

	/**
	 * Returns the current location of this host.
	 * 
	 * @return The location
	 */
	public Coord getLocation()
	{
		return this.location;
	}

	/**
	 * Returns the Path this node is currently traveling or null if no path is in use at the moment.
	 * 
	 * @return The path this node is traveling
	 */
	public Path getPath()
	{
		return this.path;
	}

	/**
	 * Sets the Node's location overriding any location set by movement model
	 * 
	 * @param location
	 *            The location to set
	 */
	public void setLocation(Coord location)
	{
		this.location = location.clone();
	}

	/**
	 * Sets the Node's name overriding the default name (groupId + netAddress)
	 * 
	 * @param name
	 *            The name to set
	 */
	public void setName(String name)
	{
		this.name = name;
	}

	/**
	 * Gets the Node's name (groupId + netAddress)
	 */
	public String getName()
	{
		return this.name;
	}

	/**
	 * Gets the Node's type (pv or rsu or request)
	 */
	public int getType()
	{
		return type;
	}

	/**
	 * Gets the Node's distination
	 */
	public Coord getDestination()
	{
		return destination;
	}

	/**
	 * Returns the messages in a collection.
	 * 
	 * @return Messages in a collection
	 */
	public Collection<Message> getMessageCollection()
	{
		return this.router.getMessageCollection();
	}

	/**
	 * Returns the number of messages this node is carrying.
	 * 
	 * @return How many messages the node is carrying currently.
	 */
	public int getNrofMessages()
	{
		return this.router.getNrofMessages();
	}

	/**
	 * Returns the buffer occupancy percentage. Occupancy is 0 for empty buffer but can be over 100 if a created message is bigger than buffer space that could be freed.
	 * 
	 * @return Buffer occupancy percentage
	 */
	public double getBufferOccupancy()
	{
		double bSize = router.getBufferSize();
		double freeBuffer = router.getFreeBufferSize();
		return 100 * ((bSize - freeBuffer) / bSize);
	}

	/**
	 * Returns routing info of this host's router.
	 * 
	 * @return The routing info.
	 */
	public RoutingInfo getRoutingInfo()
	{
		return this.router.getRoutingInfo();
	}

	/**
	 * Returns the interface objects of the node
	 */
	public List<NetworkInterface> getInterfaces()
	{
		return net;
	}

	/**
	 * Find the network interface based on the index
	 */
	public NetworkInterface getInterface(int interfaceNo)
	{
		NetworkInterface ni = null;
		try
		{
			ni = net.get(interfaceNo - 1);
		}
		catch (IndexOutOfBoundsException ex)
		{
			throw new SimError("No such interface: " + interfaceNo + " at " + this);
		}
		return ni;
	}

	/**
	 * Find the network interface based on the interfacetype
	 */
	protected NetworkInterface getInterface(String interfacetype)
	{
		for (NetworkInterface ni : net)
		{
			if (ni.getInterfaceType().equals(interfacetype))
			{
				return ni;
			}
		}
		return null;
	}

	/**
	 * Force a connection event
	 */
	public void forceConnection(DTNHost anotherHost, String interfaceId, boolean up)
	{
		NetworkInterface ni;
		NetworkInterface no;

		if (interfaceId != null)
		{
			ni = getInterface(interfaceId);
			no = anotherHost.getInterface(interfaceId);

			assert (ni != null) : "Tried to use a nonexisting interfacetype " + interfaceId;
			assert (no != null) : "Tried to use a nonexisting interfacetype " + interfaceId;
		}
		else
		{
			ni = getInterface(1);
			no = anotherHost.getInterface(1);

			assert (ni.getInterfaceType().equals(no.getInterfaceType())) : "Interface types do not match.  Please specify interface type explicitly";
		}

		if (up)
		{
			ni.createConnection(no);
		}
		else
		{
			ni.destroyConnection(no);
		}
	}

	/**
	 * for tests only --- do not use!!!
	 */
	public void connect(DTNHost h)
	{
		System.err.println("WARNING: using deprecated DTNHost.connect(DTNHost)" + "\n Use DTNHost.forceConnection(DTNHost,null,true) instead");
		forceConnection(h, null, true);
	}

	/**
	 * Updates node's network layer and router.
	 * 
	 * @param simulateConnections
	 *            Should network layer be updated too
	 */
	public void update(boolean simulateConnections)
	{
		if (!isRadioActive())// 如果节点处于非激活状态，则不会执行后面的语句
		{
			// Make sure inactive nodes don't have connections
			tearDownAllConnections();
			return;// 可以将非激活状态想象成一个自私节点或者没电的传感器
		}

		if (simulateConnections)
		{
			for (NetworkInterface i : net)
			{
				i.update();// 更新所有的接口
			}
		}
		// System.out.println("update:"+this.toString());

		// 更新PV的destination
		if (this.getType() == TYPE_PV)
		{
			// if(this.location!=this.lastLocation )
			// System.out.println(this.location+" "+this.lastLocation );
			if (this.destination != this.tempDestination || this.getPath() == null)
			{
				movement.checkDestination(tempDestination, this);
				tempDestination = this.destination;
			}
		}

		this.router.update();// 更新路由

	}

	/**
	 * Tears down all connections for this host.
	 */
	private void tearDownAllConnections()
	{
		for (NetworkInterface i : net)
		{
			// Get all connections for the interface
			List<Connection> conns = i.getConnections();
			if (conns.size() == 0)
				continue;

			// Destroy all connections
			List<NetworkInterface> removeList = new ArrayList<NetworkInterface>(conns.size());
			for (Connection con : conns)
			{
				removeList.add(con.getOtherInterface(i));
			}
			for (NetworkInterface inf : removeList)
			{
				i.destroyConnection(inf);
			}
		}
	}

	/**
	 * Moves the node towards the next waypoint or waits if it is not time to move yet
	 * 
	 * @param timeIncrement
	 *            How long time the node moves
	 */
	public void move(double timeIncrement)
	{
		double possibleMovement;
		double distance;
		double dx, dy;

		if (!isMovementActive() || SimClock.getTime() < this.nextTimeToMove)
		{
			return;
		}
		if (this.destination == null)
		{
			if (!setNextWaypoint())
			{
				return;
			}
		}

		possibleMovement = timeIncrement * speed;
		distance = this.location.distance(this.destination);

		while (possibleMovement >= distance)
		{

			// node can move past its next destination
			this.location.setLocation(this.destination); // snap to destination
			possibleMovement -= distance;
			if (!setNextWaypoint())
			{ // get a new waypoint
				return; // no more waypoints left
			}
			distance = this.location.distance(this.destination);
			if (this.type == TYPE_PV)
			{
				for (int i = 0; i < this.getGoDistance().size(); i++)
				{
					this.getGoDistance().set(i, this.getGoDistance().get(i) + distance);
				}
			}
		}

		// move towards the point for possibleMovement amount
		dx = (possibleMovement / distance) * (this.destination.getX() - this.location.getX());
		dy = (possibleMovement / distance) * (this.destination.getY() - this.location.getY());
		this.location.translate(dx, dy);
		// System.out.println(dx+" " +dy+" "+distance+ " " +possibleMovement + " " + speed);
	}

	/**
	 * Sets the next destination and speed to correspond the next waypoint on the path.
	 * 
	 * @return True if there was a next waypoint to set, false if node still should wait
	 */
	private boolean setNextWaypoint()
	{
		if (path == null)
		{
			path = movement.getPath();
		}

		if (path == null || !path.hasNext())
		{
			this.nextTimeToMove = movement.nextPathAvailable();
			this.path = null;
			return false;
		}

		this.destination = path.getNextWaypoint();
		this.speed = path.getSpeed();

		if (this.movListeners != null)
		{
			for (MovementListener l : this.movListeners)
			{
				l.newDestination(this, this.destination, this.speed);
			}
		}

		return true;
	}

	/**
	 * Sends a message from this host to another host
	 * 
	 * @param id
	 *            Identifier of the message
	 * @param to
	 *            Host the message should be sent to
	 */
	public void sendMessage(String id, DTNHost to)
	{
		System.out.println("send:" + id + " " + to);
		this.router.sendMessage(id, to);
	}

	// 收到消息
	/**
	 * Start receiving a message from another host
	 * 
	 * @param m
	 *            The message
	 * @param from
	 *            Who the message is from
	 * @return The value returned by {@link MessageRouter#receiveMessage(Message, DTNHost)}
	 */
	public int receiveMessage(Message m, DTNHost from)
	{

		int retVal = this.router.receiveMessage(m, from);
		if (retVal == MessageRouter.RCV_OK)
		{
			m.addNodeOnPath(this); // add this node on the messages path

			// System.out.println(from + "--(" + m + ")->" + this.getName());

			// System.out.println(this.getName() + "收到来自"+from+"消息" + m);
			// 加上接收后的处理

			// RSU收到消息
			if (this.type == TYPE_RSU && m.getProperty("fromNode").equals(this.getName()))
			{
				long startTime = System.currentTimeMillis();
				System.out.println("┏━━━━" + from + "---" + m + "-->" + this + "━━━━┓");
				int orig = (Integer) m.getProperty("origin");
				int des = (Integer) m.getProperty("destination");
				// Double waitMin = (Double) m.getProperty("waitMin");
				// Double waitMax = (Double) m.getProperty("waitMax");
				// DTNHost exactFromNode = this;

				System.out.println(this + " " + this.pvList.size());
				System.out.println(this.pvList);
				
				//对范围内的PV进行计算
				for (int v = 0; v < this.pvList.size(); v++)
				{
					DTNHost tempPV = pvList.get(v);

					if (orig + des == 0)
					{
						return retVal;
					}
					//超载，舍去
					if (tempPV.getAcceptNumber() >= tempPV.campacity)
					{
						System.out.println(tempPV.toString() + "容量已为" + tempPV.getAcceptNumber() + "," + "拒绝" + m);
						File file = new File("result/接受请求.txt");
						try
						{
							BufferedWriter writer = new BufferedWriter(new FileWriter(file, true));
							writer.write(tempPV.toString() + "容量已满,拒绝" + m + "\r\n");
							writer.flush();
							writer.close();
						}
						catch (IOException e)
						{
							e.printStackTrace();
						}
						continue;
					}
					// System.out.println("┏━━━━" + from + "---" + m + "-->" + tempPV + "━━━━┓");
					System.out.println("od:(" + orig + "," + des + ")" + ",from:" + this);

					// System.out.println(tempPV.toString()+" ---Exactfrom:" + exactFromNode);

					tempPV.extraCost.clear();
					tempPV.flag = 0;
					System.out.println(tempPV.getName() + "开始计算" + m);
					Double temp = tempPV.movement.cost(tempPV, orig, des); // 计算响应m的最短距离
					if (temp / tempPV.movement.odDistance(m) <= DTNHost.WAIT_RATIO) // 若在等待率范围之内
					{
						if (tempPV.path != null && tempPV.path.hasNext()) // 若车有既定路线(已响应了其他请求)
						{
							tempPV.extraCost = tempPV.movement.getContinuePathByDestination(tempPV.destination, orig, des);
							if (tempPV.extraCost.size() == 0) // 如果额外消耗列表为空
							{
								System.out.println("绕行大于阈值," + tempPV.toString() + "拒绝" + m);

							}
							else
							{
								int temp1 = 0;
								System.out.println("额外花费:[代价 o d 绕行 序号]");
								for (int i = 0; i < tempPV.extraCost.size(); i++)
								{
									if (tempPV.movement.insertCheck(tempPV, m, tempPV.extraCost.get(i)))
									{

										// System.out.println(tempPV + "插入" + m + "的方法：");
										System.out.println(tempPV.extraCost.get(i));
										tempPV.flag = 1;
										temp1 = 1;
									}
									else
									{
										tempPV.extraCost.remove(i);
										i--;
									}
								}

								// List<Double> lessCost = movement.findMinLength(extraCost);
								// exactFromNode.costMap.put(tempPV, lessCost.get(3));
								if (temp1 == 1)
								{
									// System.out.println("EXACT"+exactFromNode.toString());
									System.out.println(this);
									this.costMap.put(tempPV, tempPV.movement.findMinLength(tempPV.extraCost).get(3));
									// exactFromNode.DM.put(tempPV, m);
								}
							}

						}
						else if (path == null) // 如果路线为空
						{

							// path = movement.getPathByDestination(orig, des);

							// tempPV.destination = path.getNextWaypoint();
							// System.out.println(path.getSpeed());

							// exactFromNode.costMap.put(tempPV, movement.cost(orig, des));

							this.costMap.put(tempPV, 0.0);

							// exactFromNode.DM.put(tempPV, m);
							tempPV.flag = 2;
						}
					}
					else
					{
						System.out.println("等待率为" + temp / tempPV.movement.odDistance(m) + "大于阈值," + tempPV.toString() + "拒绝" + m);
					}
				}

				/*
				 * System.out.println("++"); Iterator<Entry<Message, DTNHost>> e2 = fromDM.entrySet().iterator(); while (e2.hasNext()) { Entry<Message, DTNHost> it =
				 * e2.next(); System.out.println("Key = " + it.getKey() + ", Value = " + it.getValue());
				 * 
				 * } System.out.println("++");
				 */
				if (!costMap.isEmpty()) // costMap不为空
				{
					System.out.println(this.getName() + "开始计算");
					DTNHost tempHost = null;
					Message tempMessage = m;
					double min = -1;

					Iterator<Entry<DTNHost, Double>> entry = costMap.entrySet().iterator();
					// Iterator<Entry<DTNHost, Message>> entry2 = DM.entrySet().iterator();

					System.out.println("costMap");
					while (entry.hasNext())
					{
						Entry<DTNHost, Double> it = entry.next();
						System.out.println("Key = " + it.getKey().toString() + ", Value = " + it.getValue() + ", flag = " + it.getKey().flag);
						if (min == -1 || min != -1 && it.getValue() < min) // 找到使cost取最小值的车
						{
							tempHost = it.getKey();
							min = it.getValue();
						}
					}
					/*
					 * System.out.println("DM"); while (entry2.hasNext()) { Entry<DTNHost, Message> it2 = entry2.next(); System.out.println("Key = " +
					 * it2.getKey().toString() + ", Value = " + it2.getValue()); if (it2.getKey() == tempHost) { tempMessage = it2.getValue(); break; } }
					 */
					// System.out.println("minCost:" + min);

					if (tempHost.flag == 1)// 车的path不为空
					{
						/*
						 * System.out.println(tempHost.getName() + "extraCost"); for (int i = 0; i < tempHost.extraCost.size(); i++) {
						 * System.out.println(tempHost.extraCost.get(i)); }
						 */
						tempHost.path = tempHost.movement.insert(tempHost, tempMessage);

					}
					else if (tempHost.flag == 2)// 车的path为空
					{
						System.out.println("Messagefrom:" + tempMessage.getFrom());
						tempHost.path = tempHost.movement.getPathByDestination((Integer) tempMessage.getProperty("origin"),
								(Integer) tempMessage.getProperty("destination"));

						tempHost.waitList
								.add(tempHost.movement.cost(tempHost, (Integer) tempMessage.getProperty("origin"), (Integer) tempMessage.getProperty("destination")));
						tempHost.detourList.add(0.0);

						tempHost.serviceOrder.add(tempMessage);
						tempHost.serviceOrder.add(tempMessage);
					}

					if (tempHost.flag != 0)//更新PV所有状态
					{
						System.out.println(tempHost.toString() + "接受了" + tempMessage.toString());
						long endTime = System.currentTimeMillis();
						File file = new File("result/接受请求.txt");
						try
						{
							BufferedWriter writer = new BufferedWriter(new FileWriter(file, true));
							writer.write(tempMessage + " " + tempHost + " " + (endTime - startTime) + "\r\n");
							writer.flush();
							writer.close();
						}
						catch (IOException e)
						{
							e.printStackTrace();
						}

						tempHost.pickList.add((Integer) tempMessage.getProperty("origin"));
						tempHost.toList.add((Integer) tempMessage.getProperty("destination"));
						tempHost.odList.add(tempHost.movement.odDistance(tempMessage));
						tempHost.requestList.add(tempMessage.getFrom());
						tempHost.messageList.add(tempMessage);
						tempHost.serviceState.add(0);
						tempHost.acceptNumber++;
						tempHost.goDistance.add(0.0);
						Double s = 0.0;
						for (int i = 0; i < tempHost.detourList.size(); i++)
						{
							s = s + tempHost.detourList.get(i);
						}
						// tempHost.load = s / tempHost.acceptNumber;

						printState(tempHost);
						// System.out.println(tempHost + "容量:" + tempHost.requestNumber);
					}
				}

				costMap.clear();
			}

			// System.out.println("┗━━━━" + from + "---" + m + "-->" + this + "━━━━┛");
			// if((int)m.getProperty("transmit_times") >= 2)
			// {
			// retVal = MessageRouter.DENIED_UNSPECIFIED;
			// }
			// m.updateProperty("transmit_times", (int)m.getProperty("transmit_times")+2);
			// System.out.println(" "+m.getProperty("transmit_times")+" ");

		}

		return retVal;
	}

	//PV状态
	public void printState(DTNHost host)
	{
		System.out.println(host.toString());
		System.out.println("[起点列表]	" + host.getPickList());
		System.out.println("[终点列表]	" + host.getToList());
		System.out.println("[经过顺序]	" + host.getServiceOrder());
		// System.out.println("[请求列表] " + host.getRequestList());
		System.out.println("[消息列表]	" + host.getMessageList());
		System.out.println("[是否接载]	" + host.getServiceState());
		System.out.println("[经过距离]	" + host.getGoDistance());
		System.out.println("[等待距离]	" + host.getWaitList());
		System.out.println("[绕行距离]	" + host.getDetourList());
		System.out.println("[最短距离]	" + host.getOdList());
		System.out.println("[接受请求数]	" + host.getAcceptNumber());
		System.out.println("[已接载数]	" + host.getInServiceNumber());
		// System.out.println("[负载] " + host.getLoad());
	}

	/**
	 * Requests for deliverable message from this host to be sent trough a connection.
	 * 
	 * @param con
	 *            The connection to send the messages trough
	 * @return True if this host started a transfer, false if not
	 */
	public boolean requestDeliverableMessages(Connection con)
	{
		return this.router.requestDeliverableMessages(con);
	}

	/**
	 * Informs the host that a message was successfully transferred.
	 * 
	 * @param id
	 *            Identifier of the message
	 * @param from
	 *            From who the message was from
	 */
	public void messageTransferred(String id, DTNHost from)
	{
		this.router.messageTransferred(id, from);
	}

	/**
	 * Informs the host that a message transfer was aborted.
	 * 
	 * @param id
	 *            Identifier of the message
	 * @param from
	 *            From who the message was from
	 * @param bytesRemaining
	 *            Nrof bytes that were left before the transfer would have been ready; or -1 if the number of bytes is not known
	 */
	public void messageAborted(String id, DTNHost from, int bytesRemaining)
	{
		this.router.messageAborted(id, from, bytesRemaining);
	}

	/**
	 * Creates a new message to this host's router
	 * 
	 * @param m
	 *            The message to create
	 */
	public void createNewMessage(Message m)
	{
		this.router.createNewMessage(m);

	}

	/**
	 * Deletes a message from this host
	 * 
	 * @param id
	 *            Identifier of the message
	 * @param drop
	 *            True if the message is deleted because of "dropping" (e.g. buffer is full) or false if it was deleted for some other reason (e.g. the message got
	 *            delivered to final destination). This effects the way the removing is reported to the message listeners.
	 */
	public void deleteMessage(String id, boolean drop)
	{
		this.router.deleteMessage(id, drop);
	}

	/**
	 * Returns a string presentation of the host.
	 * 
	 * @return Host's name
	 */
	public String toString()
	{
		return name;
	}

	/**
	 * Checks if a host is the same as this host by comparing the object reference
	 * 
	 * @param otherHost
	 *            The other host
	 * @return True if the hosts objects are the same object
	 */
	public boolean equals(DTNHost otherHost)
	{
		return this == otherHost;
	}

	/**
	 * Compares two DTNHosts by their addresses.
	 * 
	 * @see Comparable#compareTo(Object)
	 */
	public int compareTo(DTNHost h)
	{
		return this.getAddress() - h.getAddress();
	}

	public List<DTNHost> getPvList()
	{
		return pvList;
	}

	public List<Integer> getPickList()
	{
		return pickList;
	}

	public List<Integer> getToList()
	{
		return toList;
	}

	public List<DTNHost> getRequestList()
	{
		return requestList;
	}

	public List<Integer> getServiceState()
	{
		return serviceState;
	}

	public int getAcceptNumber()
	{
		return acceptNumber;
	}

	public int getInServiceNumber()
	{
		return inServiceNumber;
	}

	public void setAcceptNumber(int acceptNumber)
	{
		this.acceptNumber = acceptNumber;
	}

	public void setInServiceNumber(int inServiceNumber)
	{
		this.inServiceNumber = inServiceNumber;
	}

	public List<Double> getWaitList()
	{
		return waitList;
	}

	public List<Double> getDetourList()
	{
		return detourList;
	}

	public List<Message> getMessageList()
	{
		return messageList;
	}

	public Double getLoad()
	{
		return load;
	}

	public void setLoad(Double load)
	{
		this.load = load;
	}

	public List<List<Double>> getExtraCost()
	{
		return extraCost;
	}

	public List<Message> getServiceOrder()
	{
		return serviceOrder;
	}

	public void setServiceOrder(List<Message> serviceOrder)
	{
		this.serviceOrder = serviceOrder;
	}

	public static Double getDetourRatio()
	{
		return DETOUR_RATIO;
	}

	public List<Double> getGoDistance()
	{
		return goDistance;
	}

	public List<Double> getOdList()
	{
		return odList;
	}
}
