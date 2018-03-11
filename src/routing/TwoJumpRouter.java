package routing;

import java.util.ArrayList;
import java.util.List;

import core.Connection;
import core.Coord;
import core.DTNHost;
import core.Message;
import core.Settings;

public class TwoJumpRouter extends ActiveRouter
{

	protected TwoJumpRouter(ActiveRouter r)
	{
		super(r);
	}

	public TwoJumpRouter(Settings s)
	{
		super(s);
	}

	@Override
	public void update()
	{
		super.update();
		if (isTransferring() || !canStartTransfer())
		{
			return; // can't start a new transfer
		}

		// Try only the messages that can be delivered to final recipient
		if (exchangeDeliverableMessages() != null)
		{
			return; // started a transfer
		}

		List<Message> messages = new ArrayList<Message>(this.getMessageCollection());
		tryMessagesToTwoJumpConnections(messages, getConnections());
	}

	@Override
	public MessageRouter replicate()
	{
		return new TwoJumpRouter(this);
	}

	public Connection tryMessagesToTwoJumpConnections(List<Message> messages, List<Connection> connections)
	{
		for (int j = 0; j < messages.size(); j++)
		{
			Message message = messages.get(j);
			// DTNHost to = message.getTo();
			// DTNHost from = message.getFrom();
			double minDistance;
			minDistance = 999999;

			Connection selectedConnection = null;
			for (int i = 0, n = connections.size(); i < n; i++)
			{
				Connection con = connections.get(i);
				DTNHost toHost = con.getOtherNode(getHost());
				DTNHost nowHost = getHost();

				if (toHost.getType() == DTNHost.TYPE_REQUEST)
				{
					continue;
				}

				double tempDis = nowHost.getLocation().distance(toHost.getLocation());
				if (tempDis < minDistance)
				{
					minDistance = tempDis;
					selectedConnection = con;
				}
			}
			Message started = null;
			if (selectedConnection == null)
			{
				return null;
			}
			int retVal = startTransfer(message, selectedConnection);
			if (retVal == RCV_OK)
			{
				started = message; // accepted a message, don't try others
			}
			else if (retVal > 0)
			{
				return null; // should try later -> don't bother trying others
			}
			if (started != null)
			{
				return selectedConnection;
			}
		}
		return null;
	}

	@Override
	public boolean createNewMessage(Message m)
	{
		// System.out.println(m.getDestination().getX());
		return super.createNewMessage(m);
	}
}