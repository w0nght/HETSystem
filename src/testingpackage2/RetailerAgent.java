package HETS;

import java.util.Date;
import java.util.Random;
import java.text.SimpleDateFormat;
import jade.core.AID;
import jade.core.Agent;
import jade.core.behaviours.Behaviour;
import jade.core.behaviours.CyclicBehaviour;
import jade.core.behaviours.OneShotBehaviour;
import jade.core.behaviours.ParallelBehaviour;
import jade.core.behaviours.SequentialBehaviour;
import jade.domain.DFService;
import jade.domain.FIPAException;
import jade.domain.FIPANames;
import jade.domain.FIPAAgentManagement.DFAgentDescription;
import jade.domain.FIPAAgentManagement.ServiceDescription;
import jade.lang.acl.ACLMessage;
import jade.lang.acl.MessageTemplate;

public class RetailerAgent extends Agent {
	private Double sellPrice; // how much Kwh
	private Double buyPrice; // how much Kwh
	private Double penaltyPrice; // Penalty price to buy Kwh
	private Long timeContract = (long) 3000; // months in contracts
	private Double sum = 0.0; // Total to get paid

	private PricingStrategy pricing;
	private NegotiationStrategy negotiation;

	// private Double reduceFactor; // % of discount

	public Boolean Delay(long t) {
		long t1 = System.currentTimeMillis();
		long t2 = System.currentTimeMillis();
		Boolean f = false;
		while ((t2 < (t + t1))) {
			t2 = System.currentTimeMillis();
		}
		return true;
	}

	protected void setup() {
		// Printout a welcome message

		Object[] args = this.getArguments();
		if (args != null && args.length > 0) {
			sellPrice = Double.parseDouble((String) args[0]);
			buyPrice = Double.parseDouble((String) args[1]);
			penaltyPrice = Double.parseDouble((String) args[2]);
			System.out.println(getAID().getLocalName() + "(Sell:$" + sellPrice + ", Buy:$" + buyPrice + ", Penalty:$"
					+ penaltyPrice + ") is ready.");
		} else {
			System.out.println("Retail appliance: " + getAID().getLocalName() + " cannot be used.");
		}

		SequentialBehaviour sb = new SequentialBehaviour();
		ParallelBehaviour pb = new ParallelBehaviour(ParallelBehaviour.WHEN_ANY);

		RegisterService reg = new RegisterService();
		ProvideTheSellingEnergy provide = new ProvideTheSellingEnergy();
		OfferingContract offer = new OfferingContract();
		GetPayService getPay = new GetPayService();

		sb.addSubBehaviour(reg);

		pb.addSubBehaviour(provide);
		pb.addSubBehaviour(offer);
		pb.addSubBehaviour(getPay);

		sb.addSubBehaviour(pb);

		addBehaviour(sb);

		// this.addBehaviour(new RegisterService());
		// this.addBehaviour(new ProvideTheSellingEnergy());
		// this.addBehaviour(new OfferingContract());
		// this.addBehaviour(new GetPayService());

	}

	protected void takeDown() {
		// Printout a dismissal message
		try {
			DFService.deregister(this);
		} catch (FIPAException fe) {
			fe.printStackTrace();
		}
		System.out.println("Retailer: " + getAID().getName() + " has been terminated.");
	}

	private class RegisterService extends OneShotBehaviour {

		@Override
		public void action() {
			// TODO Auto-generated method stub

			DFAgentDescription dfd = new DFAgentDescription();
			dfd.setName(getAID());
			ServiceDescription sd = new ServiceDescription();
			sd.setType("Retail");
			sd.setName(getLocalName());
			dfd.addServices(sd);
			try {
				DFService.register(RetailerAgent.this, dfd);
			} catch (FIPAException fe) {
				fe.printStackTrace();
			}

		}

	}

	private class GetPayService extends CyclicBehaviour {

		@Override
		public void action() {
			// TODO Auto-generated method stub
			MessageTemplate mt = MessageTemplate.and(MessageTemplate.MatchPerformative(ACLMessage.INFORM),
					MessageTemplate.MatchConversationId("PayService"));

			ACLMessage gotMess = receive(mt);
			if (gotMess != null) {
				sum += Double.parseDouble(gotMess.getContent());
				System.out.println(myAgent.getLocalName() + "=>" + gotMess.getSender().getLocalName() + ":$"
						+ gotMess.getContent() + "[pay]-sum:" + sum);
			}

		}
	}

	private class ProvideTheSellingEnergy extends CyclicBehaviour {

		@Override
		public void action() {
			// TODO Auto-generated method stub
			MessageTemplate mt = MessageTemplate.and(MessageTemplate.MatchPerformative(ACLMessage.ACCEPT_PROPOSAL),
					MessageTemplate.MatchConversationId("RetailerSelling"));
			ACLMessage msg = myAgent.receive(mt);
			if (msg != null) {
				System.out.println(
						myAgent.getLocalName() + "<=" + msg.getSender().getLocalName() + ":" + msg.getContent());
				// ACCEPT_PROPOSAL Message received. Inform to the HA
				ACLMessage reply = msg.createReply();
				if (reply != null) {
					reply.setPerformative(ACLMessage.INFORM);
					reply.setConversationId("RetailerSelling");
					reply.setContent("I provided service");
					System.out.println(
							myAgent.getLocalName() + "=>" + msg.getSender().getLocalName() + ":I provided service");
				} else {
					// The requested book has been sold to another buyer in the meanwhile .
					reply.setPerformative(ACLMessage.FAILURE);
					reply.setContent("I cannot provide the service");
					System.out.println(myAgent.getLocalName() + "=>" + msg.getSender().getLocalName()
							+ ":I cannot provide the service");
				}
				myAgent.send(reply);
			} 
			
			MessageTemplate tp = MessageTemplate.and(MessageTemplate.MatchPerformative(ACLMessage.REJECT_PROPOSAL),
			MessageTemplate.MatchConversationId("RetailerSelling"));
			ACLMessage ms = myAgent.receive(tp);
			if(ms!=null)
			System.out.println(
					myAgent.getLocalName() + "<=" + ms.getSender().getLocalName() + ":" + ms.getContent());

		}

	}

	private class OfferingContract extends CyclicBehaviour {
		private Double kwhRequestbuy = 0.0;
		private long t;

		@Override
		public void action() {
			// TODO Auto-generated method stub

			// Propose Selling price
			MessageTemplate mt = MessageTemplate.and(MessageTemplate.MatchPerformative(ACLMessage.CFP),
					MessageTemplate.MatchConversationId("RetailerSelling"));
			ACLMessage msg = myAgent.receive(mt);
			t = System.currentTimeMillis();
			if (msg != null) {
				// Deplay offer
				Random rand = new Random();
				long n = ((int) (Math.random() * 9 + 1)) * 1000; // delay from 1-10s
				Delay(n);
				// CFP Message received. Process it
				Double kwhRequestbuy = Double.parseDouble(msg.getContent());
				ACLMessage reply = msg.createReply();
				// reply.setConversationId("RetailerSelling");
				// System.out.println(sellPrice.toString() + "|" + penaltyPrice.toString() + "|"
				// + timeContract.toString());
				String offer = sellPrice.toString() + "|" + penaltyPrice.toString() + "|" + timeContract.toString()
						+ "|" + kwhRequestbuy.toString();
				if (kwhRequestbuy != 0) {
					// The requested book is available for sale. Reply with the price
					reply.setPerformative(ACLMessage.PROPOSE);
					reply.setConversationId("RetailerSelling");
					reply.setContent(offer);
					System.out.println(myAgent.getLocalName() + "=>" + msg.getSender().getLocalName() + ":" + offer
							+ "  [SellPrice|Penalty|Time|Kwh] (Reply Time:" + (System.currentTimeMillis() - t) + ")");
				} else {
					// The requested book is NOT available for sale.
					reply.setPerformative(ACLMessage.REFUSE);
					// reply.setConversationId("RetailerSelling");
					reply.setContent("Meaasage me expected kwh u demand!!");
					System.out.println(myAgent.getLocalName() + "=>" + msg.getSender().getLocalName()
							+ ":Meaasage me expected kwh u demand!!");
				}
				myAgent.send(reply);

			}

		}

	}

	private class OfferingToBuyEnergy extends CyclicBehaviour {
		private Double kwhRequestsell = 0.0;
		private long t;
		@Override
		public void action() {
			// TODO Auto-generated method stub
			MessageTemplate mt = MessageTemplate.and(MessageTemplate.MatchPerformative(ACLMessage.CFP),
					MessageTemplate.MatchConversationId("RetailerSelling"));
			ACLMessage msg = myAgent.receive(mt);
			t = System.currentTimeMillis();
			if (msg != null) {
				// Deplay offer
				Random rand = new Random();
				long n = ((int) (Math.random() * 9 + 1)) * 1000; // delay from 1-10s
				Delay(n);
				// CFP Message received. Process it
				Double kwhRequestsell = Double.parseDouble(msg.getContent());
				ACLMessage reply = msg.createReply();
				// reply.setConversationId("RetailerSelling");
				// System.out.println(sellPrice.toString() + "|" + penaltyPrice.toString() + "|"
				// + timeContract.toString());
				String offer = buyPrice.toString();;
				if (kwhRequestsell != 0) {
					// The requested book is available for sale. Reply with the price
					reply.setPerformative(ACLMessage.PROPOSE);
					reply.setConversationId("RetailerSelling");
					reply.setContent(offer);
					System.out.println(myAgent.getLocalName() + "=>" + msg.getSender().getLocalName() + ":$" + offer
							+ "kwh  [wanna buy] (Reply Time:" + (System.currentTimeMillis() - t) + ")");
				} else {
					// The requested book is NOT available for sale.
					reply.setPerformative(ACLMessage.REFUSE);
					// reply.setConversationId("RetailerSelling");
					reply.setContent("Meaasage me expected kwh u wanna sell!!");
					System.out.println(myAgent.getLocalName() + "=>" + msg.getSender().getLocalName()
							+ ":Meaasage me expected kwh u wanna sell!!");
					
				}
				myAgent.send(reply);
			}

		}

	}

	public interface PricingStrategy {
		public double getPrice();
	}

	public interface NegotiationStrategy {
		public double getNegotiatedPrice();
	}

	public class PricingStrategyNo1 implements PricingStrategy {
		public double getPrice() {
			return 100;
		}
	}

	public class YearlyPricingStrategy implements PricingStrategy {
		public double getPrice() {
			Date date = new Date();
			int differentYear = date.getYear() - 2015;

			return 100 * differentYear;
		}
	}

	public class NegotiationStrategyNo1 implements NegotiationStrategy {
		public double getNegotiatedPrice() {
			return 100 - 10;
		}
	}

}
