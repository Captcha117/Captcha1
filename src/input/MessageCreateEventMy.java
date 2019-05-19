/* 
 * Copyright 2010 Aalto University, ComNet
 * Released under GPLv3. See LICENSE.txt for details. 
 */
package input;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.Scanner;

import core.Coord;
import core.DTNHost;
import core.Message;
import core.World;
import movement.map.MapNode;
import movement.map.PointsOfInterest;

/**
 * External event for creating a message.
 */
public class MessageCreateEventMy extends MessageEvent
{
	private int size;
	private int responseSize;
	static private int count = 0;
	
	

	/**
	 * Creates a message creation event with a optional response request
	 * 
	 * @param from
	 *            The creator of the message
	 * @param to
	 *            Where the message is destined to
	 * @param id
	 *            ID of the message
	 * @param size
	 *            Size of the message
	 * @param responseSize
	 *            Size of the requested response message or 0 if no response is requested
	 * @param time
	 *            Time, when the message is created
	 */
	public MessageCreateEventMy(int from, int to, String id, int size, int responseSize, double time)
	{
		super(from, to, id, time);
		this.size = size;
		this.responseSize = responseSize;

	}

	/**
	 * Creates the message this event represents.
	 */
	@Override
	public void processEvent(World world)
	{
		DTNHost to = world.getNodeByAddress(this.toAddr);
		DTNHost from = world.getNodeByAddress(this.fromAddr);

		Message m = new Message(from, to, this.id, this.size);
		
		//System.out.println("MessageCreate:"+from+" "+to);
		int orig = 1;
		int des = 1;
		Double waitMin = 0.0;
		Double waitMax = 0.0;
		String rsuNo = null;

		File file = new File("destinationFile.txt");
		BufferedReader reader = null;
		try
		{
			// System.out.println("以行为单位读取文件内容，一次读一整行：");
			reader = new BufferedReader(new FileReader(file));
			String tempString = null;
			int line = 1;
			// 一次读入一行，直到读入null为文件结束
			// while ((tempString = reader.readLine()) != null) {
			// 显示行号
			// System.out.println("line " + line + ": " + tempString);
			// line++;
			// }
			for (int i = 0; i < count; i++)
			{
				tempString = reader.readLine();
			}
			tempString = reader.readLine();
			// System.out.println(tempString);
			String[] destString = tempString.split(" ");
			orig = Integer.parseInt(destString[0]);
			des = Integer.parseInt(destString[1]);
			waitMin = Double.parseDouble(destString[2]);
			waitMax = Double.parseDouble(destString[3]);
			rsuNo = destString[4];
			count++;

			reader.close();
		}
		catch (IOException e)
		{
			e.printStackTrace();
		}

		m.addProperty("origin", orig);	//请求起点
		m.addProperty("destination", des);	//请求终点
		m.addProperty("waitMin", waitMin);	//请求最小等待距离，已无效
		m.addProperty("waitMax", waitMax);	//请求最大等待距离，已无效
		//m.addProperty("fromNode", from.getBelongTo());

		m.addProperty("fromNode", "rsu"+rsuNo);	//初始RSU
		int transmit_times = 0;
		m.addProperty("transmit_times", transmit_times);
		m.setResponseSize(this.responseSize);
		from.createNewMessage(m);
	}

	@Override
	public String toString()
	{
		return super.toString() + " [" + fromAddr + "->" + toAddr + "] " + "size:" + size + " CREATE";
	}
}
