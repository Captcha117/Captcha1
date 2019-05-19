/* 
 * Copyright 2010 Aalto University, ComNet
 * Released under GPLv3. See LICENSE.txt for details. 
 */
package movement;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Queue;
import java.util.Set;

import movement.map.DijkstraPathFinder;
import movement.map.MapNode;
import movement.map.PointsOfInterest;
import movement.map.SimMap;
import core.Coord;
import core.DTNHost;
import core.Message;
import core.Settings;
import core.SettingsError;
import core.SimError;
import input.WKTMapReader;

/**
 * Map based movement model that uses Dijkstra's algorithm to find shortest paths between two random map nodes and Points Of Interest
 */
public class MessageBasedMovement extends MapBasedMovement implements SwitchableMovement
{
	/** the Dijkstra shortest path finder */
	private DijkstraPathFinder pathFinder;

	/** Points Of Interest handler */
	private PointsOfInterest pois;

	/** List Of PV's destinations */
	private List<MapNode> destinationList;

	public List<MapNode> getDestinationList()
	{
		return destinationList;
	}

	/**
	 * Creates a new movement model based on a Settings object's settings.
	 * 
	 * @param settings
	 *            The Settings object where the settings are read from
	 */
	public MessageBasedMovement(Settings settings)
	{
		super(settings);
		this.pathFinder = new DijkstraPathFinder(getOkMapNodeTypes());
		this.pois = new PointsOfInterest(getMap(), getOkMapNodeTypes(), settings, rng);
		destinationList = new ArrayList<MapNode>();
	}

	/**
	 * Copyconstructor.
	 * 
	 * @param mbm
	 *            The ShortestPathMapBasedMovement prototype to base the new object to
	 */
	protected MessageBasedMovement(MessageBasedMovement mbm)
	{
		super(mbm);
		this.pathFinder = mbm.pathFinder;
		this.pois = mbm.pois;
		destinationList = new ArrayList<MapNode>();// 每个movement的destinationList不一样
	}

	// @Override
	// public Path getPath() {
	// Path p = new Path(generateSpeed());
	// MapNode to = pois.selectDestination();
	//
	// //System.out.println(to);
	// List<MapNode> nodePath = pathFinder.getShortestPath(lastMapNode, to);
	//
	// // this assertion should never fire if the map is checked in read phase
	// assert nodePath.size() > 0 : "No path from " + lastMapNode + " to " +
	// to + ". The simulation map isn't fully connected";
	//
	// for (MapNode node : nodePath) { // create a Path from the shortest path
	// p.addWaypoint(node.getLocation());
	// }
	//
	// lastMapNode = to;
	// return p;
	// }

	// edit by spyang
	// 获取路径
	@Override
	public Path getPath()
	{
		Path p = new Path(generateSpeed());

		// p = null;

		// if(lastMapNode == null)
		// lastMapNode = new MapNode(p.getCoords().get(0));

		return p;
	}

	// 计算花费
	public Double cost(DTNHost host, int origin, int des)
	{
		MapNode pick = pois.selectExactDestination(origin);
		MapNode to = pois.selectExactDestination(des);
		// List<MapNode> nodePath = pathFinder.getShortestPath(lastMapNode, pick);
		List<MapNode> nodePath = pathFinder.getShortestPath(getMap().getNodeByCoord(new Coord(host.getDestination().getX(), host.getDestination().getY())), pick);
		// List<MapNode> nodePath2 = pathFinder.getShortestPath(pick, to);
		// System.out.println("=-=-=-=---");
		// System.out.println(host.getLocation());
		// System.out.println(host.getDestination());
		// System.out.println(lastMapNode);
		// System.out.println("=-=-=-=---");

		// List<MapNode> odPath = pathFinder.getShortestPath(pick, to);
		// Double odLength = calculateLength(odPath);
		// System.out.println("odLength:" + odLength);

		// this assertion should never fire if the map is checked in read phase

		assert nodePath.size() > 0 : "No path from " + lastMapNode + " to " + to + ". The simulation map isn't fully connected";

		return calculateLength(nodePath);
	}

	// edited by spyang
	// 当路径为空时，PV添加请求后的路径
	public Path getPathByDestination(int origin, int des)
	{
		Path p = new Path(generateSpeed());
		MapNode pick = pois.selectExactDestination(origin);
		MapNode to = pois.selectExactDestination(des);
		List<MapNode> nodePath = pathFinder.getShortestPath(lastMapNode, pick);
		List<MapNode> nodePath2 = pathFinder.getShortestPath(pick, to);

		// this assertion should never fire if the map is checked in read phase
		assert nodePath.size() > 0 : "No path from " + lastMapNode + " to " + to + ". The simulation map isn't fully connected";

		for (MapNode node : nodePath)
		{ // create a Path from the shortest path
			p.addWaypoint(node.getLocation());
		}
		for (MapNode node : nodePath2)
		{ // create a Path from the shortest path
			p.addWaypoint(node.getLocation());
		}

		System.out.println("lastMapNode:" + lastMapNode);

		lastMapNode = to;
		destinationList.add(pick);
		destinationList.add(to);
		System.out.println("host:" + host.getName());
		System.out.println("pick:" + pick + "\nto:" + to);
		System.out.println("last->pick:" + calculateLength(nodePath));
		System.out.println("pick->to:" + calculateLength(nodePath2));

		System.out.println("destinationList:");
		for (int i = 0; i < destinationList.size(); i++)
		{
			System.out.print(destinationList.get(i) + " ");
		}
		System.out.println();

		return p;
	}

	// 插入算法计算路径
	public List<List<Double>> getContinuePathByDestination(Coord location, int orig, int des)
	{
		// 表示乘客的起点和目的地是否在当前的路径地点集合中的变量judge
		int judge1 = 0;
		int judge2 = 0;

		Path p = new Path(generateSpeed());
		MapNode pick = pois.selectExactDestination(orig);
		MapNode to = pois.selectExactDestination(des);
		// MapNode nowLocation = getMap().getNodeByCoord(new Coord(location.getX()-14142.359,-location.getY()+60464.955));
		MapNode nowLocation = getMap().getNodeByCoord(new Coord(location.getX(), location.getY()));

		List<MapNode> odPath = pathFinder.getShortestPath(pick, to);
		Double odLength = calculateLength(odPath);
		System.out.println(odLength);

		for (int i = 0; i < destinationList.size(); i++)
		{
			if (Math.abs(pick.getLocation().getX() - destinationList.get(i).getLocation().getX()) < 0.0001
					&& Math.abs(pick.getLocation().getY() - destinationList.get(i).getLocation().getY()) < 0.0001)
				judge1 = 1;
			if (Math.abs(to.getLocation().getX() - destinationList.get(i).getLocation().getX()) < 0.0001
					&& Math.abs(to.getLocation().getY() - destinationList.get(i).getLocation().getY()) < 0.0001)
				judge2 = 1;
		}

		List<List<Double>> extraCost = new ArrayList<List<Double>>();
		List<MapNode> ExtraNodePath1 = new ArrayList<MapNode>();
		List<MapNode> ExtraNodePath2 = new ArrayList<MapNode>();
		List<MapNode> ExtraNodePath3 = new ArrayList<MapNode>();
		List<MapNode> ExtraNodePath4 = new ArrayList<MapNode>();
		List<MapNode> ExtraNodePath5 = new ArrayList<MapNode>();
		List<MapNode> ExtraNodePath6 = new ArrayList<MapNode>();

		// 1 若将pick和to安排在相邻的位置
		// 1.1若pick和to相邻且在路径最前面，在当前位置之后
		ExtraNodePath1 = pathFinder.getShortestPath(nowLocation, pick);
		ExtraNodePath2 = pathFinder.getShortestPath(pick, to);
		ExtraNodePath3 = pathFinder.getShortestPath(to, destinationList.get(0));

		ExtraNodePath4 = pathFinder.getShortestPath(nowLocation, destinationList.get(0));
		Double cost1 = calculateLength(ExtraNodePath1) + calculateLength(ExtraNodePath2) + calculateLength(ExtraNodePath3) - calculateLength(ExtraNodePath4);
		if (cost1 / odLength <= DTNHost.DETOUR_RATIO)
		{
			List<Double> subCostList1 = new ArrayList<Double>();
			subCostList1.add(cost1); // 代价
			subCostList1.add(0.0); // 乘客的pick点要插入的位置
			// subCostList1.add(0.0); // 乘客的to点要插入的位置
			subCostList1.add(1.0);
			subCostList1.add(cost1 / odLength);
			subCostList1.add(1.0);//
			extraCost.add(subCostList1);
		}

		// 1.2若pick和to相邻且在路径的最尾部
		ExtraNodePath1 = pathFinder.getShortestPath(destinationList.get(destinationList.size() - 1), pick);
		ExtraNodePath2 = pathFinder.getShortestPath(pick, to);
		Double cost2 = calculateLength(ExtraNodePath1) + calculateLength(ExtraNodePath2);
		if (cost2 / odLength <= DTNHost.DETOUR_RATIO)
		{
			List<Double> subCostList2 = new ArrayList<Double>();
			subCostList2.add(cost2); // 代价
			subCostList2.add(destinationList.size() + 0.0); // 乘客的pick点要插入的位置
			// subCostList2.add(destinationList.size() + 0.0); // 乘客的to点要插入的位置
			subCostList2.add(destinationList.size() + 1.0);
			subCostList2.add(cost2 / odLength);
			subCostList2.add(2.0);//
			extraCost.add(subCostList2);
		}

		// 1.3若pick和to相邻，且不在路径最前面也不在路径的最尾部
		for (int i = 0; i < destinationList.size() - 1; i++)
		{
			ExtraNodePath1 = pathFinder.getShortestPath(destinationList.get(i), pick);
			ExtraNodePath2 = pathFinder.getShortestPath(pick, to);
			ExtraNodePath3 = pathFinder.getShortestPath(to, destinationList.get(i + 1));

			ExtraNodePath4 = pathFinder.getShortestPath(destinationList.get(i), destinationList.get(i + 1));
			Double cost3 = calculateLength(ExtraNodePath1) + calculateLength(ExtraNodePath2) + calculateLength(ExtraNodePath3) - calculateLength(ExtraNodePath4);
			if (cost3 / odLength <= DTNHost.DETOUR_RATIO)
			{
				List<Double> subCostList3 = new ArrayList<Double>();
				subCostList3.add(cost3); // 代价
				subCostList3.add(i + 1 + 0.0); // 乘客的pick点要插入的位置
				// subCostList3.add(i + 1 + 0.0); // 乘客的to点要插入的位置
				subCostList3.add(i + 2 + 0.0);
				subCostList3.add(cost3 / odLength);
				subCostList3.add(3.0);//
				extraCost.add(subCostList3);
			}
		}

		// 2 若将pick和to安排在不相邻的位置
		// 2.1若pick和to不相邻且pick在路径最前面，to在路径的最后面
		ExtraNodePath1 = pathFinder.getShortestPath(nowLocation, pick);
		ExtraNodePath2 = pathFinder.getShortestPath(pick, destinationList.get(0));
		ExtraNodePath3 = pathFinder.getShortestPath(destinationList.get(destinationList.size() - 1), to);

		ExtraNodePath4 = pathFinder.getShortestPath(nowLocation, destinationList.get(0));
		Double cost4 = calculateLength(ExtraNodePath1) + calculateLength(ExtraNodePath2) + calculateLength(ExtraNodePath3) - calculateLength(ExtraNodePath4);
		if (cost4 / odLength <= DTNHost.DETOUR_RATIO)
		{
			List<Double> subCostList4 = new ArrayList<Double>();
			subCostList4.add(cost4); // 代价
			subCostList4.add(0.0); // 乘客的pick点要插入的位置
			// subCostList4.add(destinationList.size() + 0.0); // 乘客的to点要插入的位置
			subCostList4.add(destinationList.size() + 1.0);
			subCostList4.add(cost4 / odLength);
			subCostList4.add(4.0);//
			extraCost.add(subCostList4);
		}
		// 2.2若pick和to不相邻且pick在路径的最前面，to不在路径的最后面
		for (int i = 0; i < destinationList.size() - 1; i++)
		{
			ExtraNodePath1 = pathFinder.getShortestPath(nowLocation, pick);
			ExtraNodePath2 = pathFinder.getShortestPath(pick, destinationList.get(0));
			ExtraNodePath3 = pathFinder.getShortestPath(destinationList.get(i), to);
			ExtraNodePath4 = pathFinder.getShortestPath(to, destinationList.get(i + 1));

			ExtraNodePath5 = pathFinder.getShortestPath(nowLocation, destinationList.get(0));
			ExtraNodePath6 = pathFinder.getShortestPath(destinationList.get(i), destinationList.get(i + 1));

			Double cost5 = calculateLength(ExtraNodePath1) + calculateLength(ExtraNodePath2) + calculateLength(ExtraNodePath3) + calculateLength(ExtraNodePath4)
					- calculateLength(ExtraNodePath5) - calculateLength(ExtraNodePath6);
			if (cost5 / odLength <= DTNHost.DETOUR_RATIO)
			{
				List<Double> subCostList5 = new ArrayList<Double>();
				subCostList5.add(cost5); // 代价
				subCostList5.add(0.0); // 乘客的pick点要插入的位置
				// subCostList5.add(i + 1 + 0.0); // 乘客的to点要插入的位置
				subCostList5.add(i + 2 + 0.0);
				subCostList5.add(cost5 / odLength);
				subCostList5.add(5.0);
				extraCost.add(subCostList5);
			}
		}
		// 2.3若pick和to不相邻且pick不在路径的最前面，to在路径的最后面
		for (int i = 0; i < destinationList.size() - 1; i++)
		{
			ExtraNodePath1 = pathFinder.getShortestPath(destinationList.get(i), pick);
			ExtraNodePath2 = pathFinder.getShortestPath(pick, destinationList.get(i + 1));
			ExtraNodePath3 = pathFinder.getShortestPath(destinationList.get(destinationList.size() - 1), to);

			ExtraNodePath4 = pathFinder.getShortestPath(destinationList.get(i), destinationList.get(i + 1));
			Double cost6 = calculateLength(ExtraNodePath1) + calculateLength(ExtraNodePath2) + calculateLength(ExtraNodePath3) - calculateLength(ExtraNodePath4);
			if (cost6 / odLength <= DTNHost.DETOUR_RATIO)
			{
				List<Double> subCostList6 = new ArrayList<Double>();
				subCostList6.add(cost6); // 代价
				subCostList6.add(i + 1 + 0.0); // 乘客的pick点要插入的位置
				// subCostList6.add(destinationList.size() + 0.0); // 乘客的to点要插入的位置
				subCostList6.add(destinationList.size() + 1.0);
				subCostList6.add(cost6 / odLength);
				subCostList6.add(6.0);
				extraCost.add(subCostList6);
			}
		}
		// 2.4若pick和to不相邻且pick不在路径的最前面，to不在路径的最后面
		for (int i = 0; i < destinationList.size() - 2; i++)
		{
			for (int j = i + 1; j < destinationList.size() - 1; j++)
			{
				ExtraNodePath1 = pathFinder.getShortestPath(destinationList.get(i), pick);
				ExtraNodePath2 = pathFinder.getShortestPath(pick, destinationList.get(i + 1));
				ExtraNodePath3 = pathFinder.getShortestPath(destinationList.get(j), to);
				ExtraNodePath4 = pathFinder.getShortestPath(to, destinationList.get(j + 1));

				ExtraNodePath5 = pathFinder.getShortestPath(destinationList.get(i), destinationList.get(i + 1));
				ExtraNodePath6 = pathFinder.getShortestPath(destinationList.get(j), destinationList.get(j + 1));

				Double cost7 = calculateLength(ExtraNodePath1) + calculateLength(ExtraNodePath2) + calculateLength(ExtraNodePath3) + calculateLength(ExtraNodePath4)
						- calculateLength(ExtraNodePath5) - calculateLength(ExtraNodePath6);
				if (cost7 / odLength <= DTNHost.DETOUR_RATIO)
				{
					List<Double> subCostList7 = new ArrayList<Double>();
					subCostList7.add(cost7); // 代价
					subCostList7.add(i + 1 + 0.0); // 乘客的pick点要插入的位置
					// subCostList7.add(j + 1 + 0.0); // 乘客的to点要插入的位置
					subCostList7.add(j + 2 + 0.0);
					subCostList7.add(cost7 / odLength);
					subCostList7.add(7.0);

					extraCost.add(subCostList7);
				}
			}
		}
		return extraCost;
	}

	// 插入判断
	public boolean insertCheck(DTNHost host, Message message, List<Double> extraCost)
	{
		int orig = (Integer) message.getProperty("origin");
		int des = (Integer) message.getProperty("destination");

		MapNode pick = pois.selectExactDestination(orig);
		MapNode to = pois.selectExactDestination(des);

		// List<List<Double>> extraCost = host.getExtraCost();
		// List<Double> insertLocation = findMinLength(extraCost);

		List<MapNode> tempDestinationList = new ArrayList<MapNode>();
		List<Message> tempServiceOrder = new ArrayList<Message>();
		for (int i = 0; i < destinationList.size(); i++)
		{
			tempDestinationList.add(destinationList.get(i));
		}
		for (int i = 0; i < host.getServiceOrder().size(); i++)
		{
			tempServiceOrder.add(host.getServiceOrder().get(i));
		}
		tempDestinationList.add((new Double(extraCost.get(1)).intValue()), pick);
		tempDestinationList.add((new Double(extraCost.get(2)).intValue()), to);
		tempServiceOrder.add((new Double(extraCost.get(1)).intValue()), message);
		tempServiceOrder.add((new Double(extraCost.get(2)).intValue()), message);
		List<Double> tempDistanceList = new ArrayList<Double>();
		List<MapNode> odPath = pathFinder.getShortestPath(pick, to);
		Double odLength = calculateLength(odPath);

		for (int k = 0; k < tempDestinationList.size() - 1; k++)
		{
			if (k == 0)
			{
				tempDistanceList.add(tempDestinationList.get(k).getLocation().distance(host.getDestination()));
			}
			tempDistanceList.add(tempDestinationList.get(k).getLocation().distance(tempDestinationList.get(k + 1).getLocation()));
		}

		// 判断插入后其他请求的绕行率与等待率
		for (int i = 0; i < host.getMessageList().size(); i++)
		{
			if (host.getPickList().get(i) != -1)
			{
				int index1 = -1;
				int index2 = -1;
				for (int j = 0; j < tempServiceOrder.size(); j++)
				{
					if (tempServiceOrder.get(j) == host.getMessageList().get(i))
					{
						if (index1 == -1)
						{
							index1 = j;
						}
						else
						{
							index2 = j;
						}
						if (j == 0)
						{
							break;
						}
						if (index1 == j)
						{
							Double tempDistance = 0.0;
							for (int k = 0; k < j; k++)
							{
								tempDistance += tempDistanceList.get(k);
							}
							if (tempDistance / odLength <= DTNHost.WAIT_RATIO)
							{
								break;
							}
							else
							{
								System.out.println(host.toString() + extraCost + "插入" + message.toString() + "后,导致" + tempServiceOrder.get(j) + "的等待率变为"
										+ tempDistance / odLength + ",故拒绝");
								return false;
							}
						}
						else
						{
							Double tempDistance = 0.0;
							for (int k = index1; k < index2; k++)
							{
								tempDistance += tempDistanceList.get(k);
							}
							if (tempDistance / odLength <= DTNHost.DETOUR_RATIO)
							{
								break;
							}
							else
							{
								System.out.println(
										host.toString() + "插入" + message.toString() + "后,导致" + tempServiceOrder.get(j) + "的绕行率变为" + tempDistance / odLength + ",故拒绝");
								return false;
							}
						}
					}
				}
			}
			else
			{
				for (int j = 0; j < tempServiceOrder.size(); j++)
				{
					if (tempServiceOrder.get(j) == host.getMessageList().get(i))
					{
						Double tempDistance = host.getGoDistance().get(i);
						for (int k = 0; k < j; k++)
						{
							tempDistance += tempDistanceList.get(k);
						}
						if (tempDistance / odLength <= DTNHost.DETOUR_RATIO)
						{
							break;
						}
						else
						{
							System.out.println(
									host.toString() + "插入" + message.toString() + "后,导致" + tempServiceOrder.get(j) + "的绕行率变为" + tempDistance / odLength + ",故拒绝");
							return false;
						}
					}
				}
			}
		}
		return true;
	}

	// 路径插入
	public Path insert(DTNHost host, Message message)
	{
		Coord location = host.getDestination();
		int orig = (Integer) message.getProperty("origin");
		int des = (Integer) message.getProperty("destination");
		List<List<Double>> extraCost = host.getExtraCost();

		System.out.println("insert:" + location.toString() + " " + orig + " " + des);

		Path p = new Path(generateSpeed());
		MapNode pick = pois.selectExactDestination(orig);
		MapNode to = pois.selectExactDestination(des);
		MapNode nowLocation = getMap().getNodeByCoord(new Coord(location.getX(), location.getY()));
		List<Double> insertLocation = findMinLength(extraCost);

		List<MapNode> waitPath = pathFinder.getShortestPath(lastMapNode, pick);
		Double waitLength = calculateLength(waitPath);
		host.getWaitList().add(waitLength);

		System.out.println("now:" + nowLocation.toString());

		List<MapNode> odPath = pathFinder.getShortestPath(pick, to);
		Double odLength = calculateLength(odPath);

		host.getDetourList().add(insertLocation.get(3));
		// host.setLoad(host.getLoad() + insertLocation.get(3));
		host.getServiceOrder().add((new Double(insertLocation.get(1)).intValue()), message);
		host.getServiceOrder().add((new Double(insertLocation.get(2)).intValue()), message);

		List<Double> tempDistanceList = new ArrayList<Double>();
		destinationList.add((new Double(insertLocation.get(1)).intValue()), pick);
		destinationList.add((new Double(insertLocation.get(2)).intValue()), to);
		for (int k = 0; k < destinationList.size() - 1; k++)
		{
			if (k == 0)
			{
				tempDistanceList.add(destinationList.get(k).getLocation().distance(host.getDestination()));
			}
			tempDistanceList.add(destinationList.get(k).getLocation().distance(destinationList.get(k + 1).getLocation()));
		}

		for (int i = 0; i < host.getMessageList().size(); i++)
		{
			if (host.getPickList().get(i) != -1)
			{
				int index1 = -1;
				int index2 = -1;
				for (int j = 0; j < host.getServiceOrder().size(); j++)
				{
					if (host.getServiceOrder().get(j) == host.getMessageList().get(i))
					{
						if (index1 == -1)
						{
							index1 = j;
						}
						else
						{
							index2 = j;
						}
						if (j == 0)
						{
							break;
						}
						if (index1 == j)
						{
							Double tempDistance = 0.0;
							for (int k = 0; k < j; k++)
							{
								tempDistance += tempDistanceList.get(k);
							}
							if (tempDistance >= (Double) host.getMessageList().get(i).getProperty("waitMin")
									&& tempDistance <= (Double) host.getMessageList().get(i).getProperty("waitMax"))
							{
								host.getWaitList().set(i, tempDistance);
								break;
							}
						}
						else
						{
							Double tempDistance = 0.0;
							for (int k = index1; k < index2; k++)
							{
								tempDistance += tempDistanceList.get(k);
							}
							if (tempDistance / odLength <= DTNHost.DETOUR_RATIO)
							{
								break;
							}
						}
					}
				}
			}
			else
			{
				for (int j = 0; j < host.getServiceOrder().size(); j++)
				{
					if (host.getServiceOrder().get(j) == host.getMessageList().get(i))
					{
						Double tempDistance = host.getGoDistance().get(i);
						for (int k = 0; k < j; k++)
						{
							tempDistance += tempDistanceList.get(k);
						}
						if (tempDistance / odLength <= DTNHost.DETOUR_RATIO)
						{
							host.getDetourList().set(i, tempDistance / odLength);
							break;
						}
					}
				}
			}
		}

		List<List<MapNode>> pathList = new ArrayList<List<MapNode>>();
		for (int i = 0; i < destinationList.size() - 1; i++)
		{
			if (i == 0)
			{
				pathList.add(pathFinder.getShortestPath(nowLocation, destinationList.get(i)));

			}
			pathList.add(pathFinder.getShortestPath(destinationList.get(i), destinationList.get(i + 1)));
		}

		for (int i = 0; i < pathList.size(); i++)
		{
			for (int j = 0; j < pathList.get(i).size(); j++)
			{
				p.addWaypoint(pathList.get(i).get(j).getLocation());
			}
		}

		/*
		 * System.out.println(host.toString() + " destinationList:"); for (int i = 0; i < destinationList.size(); i++) { System.out.print(destinationList.get(i) + " "); }
		 * System.out.println("\npathList:"); for (int i = 0; i < pathList.size(); i++) { System.out.println(pathList.get(i)); } System.out.println();
		 */
		lastMapNode = destinationList.get(destinationList.size() - 1);
		for (int j = 0; j < destinationList.size(); j++)
		{
			System.out.print(destinationList.get(j) + " ");
		}
		System.out.println();
		return p;
	}

	// 计算路径长度
	public Double calculateLength(List<MapNode> nodePath)
	{
		MapNode pre = nodePath.get(0);
		Double length = 0.0;
		for (int i = 1; i < nodePath.size(); i++)
		{
			length = length + Math.sqrt(Math.pow((pre.getLocation().getX() - nodePath.get(i).getLocation().getX()), 2)
					+ Math.pow((pre.getLocation().getY() - nodePath.get(i).getLocation().getY()), 2));
			pre = nodePath.get(i);
		}
		return length;
	}

	// 寻找最短路径
	public List<Double> findMinLength(List<List<Double>> extraCost)
	{
		List<Double> temp = new ArrayList<Double>();
		Double minValue;
		// int minIndex;
		minValue = 999999999999.999;
		// minIndex = 0;
		// List<Integer> retuV = new ArrayList<Integer>();
		// retuV.add(0);
		// retuV.add(0);

		for (int i = 0; i < extraCost.size(); i++)
		{
			if (extraCost.get(i).get(3) < minValue)
			{
				minValue = extraCost.get(i).get(3);
				// double orig = Double.valueOf(extraCost.get(i).get(1));
				// double des = Double.valueOf(extraCost.get(i).get(2));
				// retuV.set(0, (int) orig);
				// retuV.set(1, (int) des);

				temp = extraCost.get(i);
			}
		}

		System.out.println("最小绕行率:" + minValue);
		return temp;
	}

	// 判断PV是否到达目的地
	public void checkDestination(Coord tempDestination, DTNHost host)
	{
		for (int i = 0; i < destinationList.size(); i++)
		{
			if (destinationList.get(i) == getMap().getNodeByCoord(new Coord(tempDestination.getX(), tempDestination.getY())))
			{
				System.out.println("destinationList:" + host.toString());
				for (int j = 0; j < destinationList.size(); j++)
				{
					System.out.print(destinationList.get(j) + " ");
				}
				System.out.println();

				if (checkPick(tempDestination, host) + checkTo(tempDestination, host) != 0)
				{
					destinationList.remove(i);
					i--;
				}
			}
		}

	}

	// 判断目的地是否为请求起点
	public int checkPick(Coord tempDestination, DTNHost host)
	{
		int flag = 0;
		for (int i = 0; i < host.getPickList().size(); i++)
		{
			if (host.getPickList().get(i) == -1)
			{
				continue;
			}
			else if (pois.selectExactDestination(host.getPickList().get(i)) == getMap().getNodeByCoord(new Coord(tempDestination.getX(), tempDestination.getY())))
			{
				flag = 1;
				Message tempMessage = host.getMessageList().get(i);
				host.getPickList().set(i, -1);
				host.getServiceState().set(i, 1);

				File file = new File("result/上车.txt");
				try
				{
					BufferedWriter writer = new BufferedWriter(new FileWriter(file, true));
					writer.write(tempMessage + " ↑ " + host.getGoDistance().get(i) + " " + (host.getGoDistance().get(i) / host.getOdList().get(i)) + "\r\n");
					writer.flush();
					writer.close();
				}
				catch (IOException e)
				{
					e.printStackTrace();
				}

				for (int j = 0; j < host.getServiceOrder().size(); j++)
				{
					if (host.getServiceOrder().get(j) == tempMessage)
					{
						host.getServiceOrder().remove(j);
						break;
					}
				}

				host.setInServiceNumber(host.getInServiceNumber() + 1);
				host.printState(host);
				break;
			}
		}
		return flag;
	}

	// 判断目的地是否为请求终点
	public int checkTo(Coord tempDestination, DTNHost host)
	{
		int flag = 0;
		for (int i = 0; i < host.getToList().size(); i++)
		{
			if (pois.selectExactDestination(host.getToList().get(i)) == getMap().getNodeByCoord(new Coord(tempDestination.getX(), tempDestination.getY()))
					&& host.getPickList().get(i) == -1)
			{
				flag = 1;
				Message tempMessage = host.getMessageList().get(i);

				File file = new File("result/下车.txt");
				try
				{
					BufferedWriter writer = new BufferedWriter(new FileWriter(file, true));
					writer.write(tempMessage + " ↓ " + (host.getGoDistance().get(i)) + " "
							+ ((host.getGoDistance().get(i) - host.getWaitList().get(i)) / host.getOdList().get(i)) + "\r\n");
					writer.flush();
					writer.close();
				}
				catch (IOException e)
				{
					e.printStackTrace();
				}

				for (int j = 0; j < host.getServiceOrder().size(); j++)
				{
					if (host.getServiceOrder().get(j) == tempMessage)
					{
						host.getServiceOrder().remove(j);
						break;
					}
				}
				host.getPickList().remove(i);
				host.getToList().remove(i);
				host.getRequestList().remove(i);
				host.getServiceState().remove(i);
				host.getWaitList().remove(i);
				host.getOdList().remove(i);
				host.getMessageList().remove(i);
				host.getDetourList().remove(i);
				host.getGoDistance().remove(i);
				host.setAcceptNumber(host.getAcceptNumber() - 1);
				host.setInServiceNumber(host.getInServiceNumber() - 1);

				host.printState(host);
				/*
				 * if (host.getToList().size() == 0) { host.setLoad(0.0); } else { Double s = 0.0; for (int j = 0; j < host.getDetourList().size(); j++) { s = s +
				 * host.getDetourList().get(j); } host.setLoad(s / host.getAcceptNumber()); }
				 */
				i--;
				break;
			}
		}

		return flag;

	}

	@Override
	public MessageBasedMovement replicate()
	{
		return new MessageBasedMovement(this);
	}

	// 计算OD距离
	public Double odDistance(Message m)
	{
		MapNode pick = pois.selectExactDestination((Integer) m.getProperty("origin"));
		MapNode to = pois.selectExactDestination((Integer) m.getProperty("destination"));
		List<MapNode> nodePath = pathFinder.getShortestPath(pick, to);
		return calculateLength(nodePath);
	}
}
