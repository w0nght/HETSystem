package HETS;

import java.util.Date;


import jade.core.AID;
import jade.core.Agent;
import jade.core.behaviours.Behaviour;
import jade.core.behaviours.CyclicBehaviour;
import jade.core.behaviours.OneShotBehaviour;
import jade.core.behaviours.ParallelBehaviour;
import jade.core.behaviours.SequentialBehaviour;
import jade.core.behaviours.TickerBehaviour;
import jade.domain.DFService;
import jade.domain.FIPAException;
import jade.domain.FIPANames;
import jade.domain.FIPAAgentManagement.DFAgentDescription;
import jade.domain.FIPAAgentManagement.ServiceDescription;
import jade.lang.acl.ACLMessage;
import jade.lang.acl.MessageTemplate;

public class HomeAgent extends Agent {
	private Double usage = 5.5; // (total Kwh apps consumed)
	private Double store = 0.0; // (total Kwh apps generated)
	private String contract; // (SellingPrice|PenaltyPrice|Kwh)
	private Long timeSlot = (long) 0; // How many hours Contract will be renew
	private Double maxBuy = 1.1; // maximum price to buy
	private Double minSell = 1.2; // minimum price to Sell
	private Double money; // credit to pay
	private AID[] retailers; // All retails
	private AID[] conApps; // All consuming apps
	private AID[] genApps; // All generating apps
	private AID lastRetailer;

	protected void setup() {
		// Printout a welcome message
		Object[] args = this.getArguments();
		if (args != null && args.length > 0) {
			money = Double.parseDouble((String) args[0]);
			System.out.println(getAID().getLocalName() + ": $" + money + " is ready.");
		}

		
		SequentialBehaviour seq = new SequentialBehaviour();
		ParallelBehaviour pb = new ParallelBehaviour(ParallelBehaviour.WHEN_ANY);

		RegisterService reg = new RegisterService();
		UpdatingApps apps = new UpdatingApps();
		UpdatingRetailers res = new UpdatingRetailers();
		LookingContract loo = new LookingContract();
		AnswerConsumeRequests con = new AnswerConsumeRequests();
		AnswerStoreRequests sto = new AnswerStoreRequests();
		
		pb.addSubBehaviour(new TickerBehaviour(this, 2000) {
			@Override
			protected void onTick() {
				// TODO Auto-generated method stub
				addBehaviour(new PayService());
			}
		});
		pb.addSubBehaviour(apps);
		pb.addSubBehaviour(res);
		pb.addSubBehaviour(loo);
		pb.addSubBehaviour(con);
		pb.addSubBehaviour(sto);
		
		seq.addSubBehaviour(reg);
		seq.addSubBehaviour(pb);
		
		
		
		addBehaviour(seq);
		//this.addBehaviour(new RegisterService());
		//this.addBehaviour(new UpdatingApps());
		//this.addBehaviour(new UpdatingRetailers());
		//this.addBehaviour(new LookingContract());
		//this.addBehaviour(new AnswerConsumeRequests());
		//this.addBehaviour(new AnswerStoreRequests());

	}

	protected void takeDown() {

		try {
			DFService.deregister(this);
		} catch (FIPAException fe) {
			fe.printStackTrace();
		}
		// Printout a dismissal message
		System.out.println(getAID().getLocalName() + " has been terminated.");
	}

	private class RegisterService extends OneShotBehaviour {

		@Override
		public void action() {
			// TODO Auto-generated method stub

			DFAgentDescription dfd = new DFAgentDescription();
			dfd.setName(getAID());
			ServiceDescription sd = new ServiceDescription();
			sd.setType("HomeAgent");
			sd.setName(getLocalName());
			dfd.addServices(sd);
			try {
				DFService.register(HomeAgent.this, dfd);
			} catch (FIPAException fe) {
				fe.printStackTrace();
			}

		}

	}

	private class LookingContract extends Behaviour {
		private int step = 0;
		private MessageTemplate mt; // The template to receive replies
		private AID bestSeller; // The agent who provides the best offer
		private Double bestPrice; // The best offered price
		private int repCount = 0; // The counter of replies from retailers
		private long timeAllow; // timeAllow to get offer from retailers after send cfp
		private Double offerPrice;
		private Double offerPenalty;
		private Double offerKwh;
		private long offerTime;

		@Override
		public void action() {
			// TODO Auto-generated method stub
			if (timeSlot == 0) {
				switch (step) {
				case 0:
					// Send the cfp to all sellers
					ACLMessage cfp = new ACLMessage(ACLMessage.CFP);
					cfp.setConversationId("RetailerSelling");
					for (int i = 0; i < retailers.length; ++i) {
						cfp.addReceiver(retailers[i]);
						System.out.println(myAgent.getLocalName() + "=>" + retailers[i].getLocalName() + ":"
								+ usage.toString() + "[wanna buy]");
					}
					cfp.setContent(usage.toString());
					cfp.setConversationId("RetailerSelling");
					timeAllow = System.currentTimeMillis() + 500;
					cfp.setReplyByDate(new Date(timeAllow));
					myAgent.send(cfp);

					step++;

					// Prepare the template to get proposals
					mt = MessageTemplate.and(MessageTemplate.MatchConversationId("RetailerSelling"),
							MessageTemplate.MatchInReplyTo(cfp.getReplyWith()));
					break;

				case 1:
					// Receive all proposals/refusals from retailers
					ACLMessage reply = receive();

					if (reply != null) {
						// Reply received
						if (reply.getPerformative() == ACLMessage.PROPOSE) {
							String[] parts = reply.getContent().split("\\|");
							offerPrice = Double.parseDouble(parts[0].toString());
							offerPenalty = Double.parseDouble(parts[1]);
							offerTime = Long.parseLong(parts[2].toString());
							offerKwh = Double.parseDouble(parts[3].toString());
							System.out.println(myAgent.getLocalName() + "<=" + reply.getSender().getLocalName() + ":"
									+ reply.getContent());
							if (bestSeller == null || offerPrice < bestPrice) {
								// This is the best offer at present
								bestPrice = offerPrice;
								bestSeller = reply.getSender();
							}else {
								ACLMessage refuse = new ACLMessage(ACLMessage.REJECT_PROPOSAL);
								refuse.addReceiver(bestSeller);
								refuse.setContent("Refuse");
								refuse.setConversationId("RetailerSelling");
								// refuse.setReplyWith("I'll refuse" + System.currentTimeMillis());
								System.out.println(myAgent.getLocalName() + "=>" + reply.getSender().getLocalName()
										+ ":refused[found a better price]");
								send(refuse);
								step = 4;
								
							}

							repCount++;
							if ((repCount >= retailers.length) || (timeAllow >= System.currentTimeMillis())) {
								// If ( received all retailers or over timeAllow)
								if (bestSeller != null) {
									
									if (maxBuy >= bestPrice) // If bestprice <= maxprice, next else refuse
										step++;
									else {
										ACLMessage refuse = new ACLMessage(ACLMessage.REJECT_PROPOSAL);
										refuse.addReceiver(bestSeller);
										refuse.setContent("Refuse");
										refuse.setConversationId("RetailerSelling");
										// refuse.setReplyWith("I'll refuse" + System.currentTimeMillis());
										System.out.println(myAgent.getLocalName() + "=>" + bestSeller.getLocalName()
												+ ":Refuse[Over Max Buy Price]");
										send(refuse);
										step = 0;
									}
								}else step=0;
							}
							break;
						}
					}

					break;
				case 2:
					// Send the purchase order to the retailer that provided the best offer
					ACLMessage accept = new ACLMessage(ACLMessage.ACCEPT_PROPOSAL);
					accept.addReceiver(bestSeller);
					accept.setContent("Accepted");
					accept.setConversationId("RetailerSelling");
					accept.setReplyWith("I'll buy" + System.currentTimeMillis());
					myAgent.send(accept);
					System.out.println(myAgent.getLocalName() + "=>" + bestSeller.getLocalName() + ":Accepted");
					// Prepare the template to get the purchase reply from best retailer
					mt = MessageTemplate.and(MessageTemplate.MatchConversationId("RetailerSelling"),
							MessageTemplate.MatchInReplyTo(accept.getReplyWith()));
					step++;
					break;
				case 3:
					// Receive provided service
					reply = myAgent.receive(mt);
					if (reply != null) {
						if (reply.getPerformative() == ACLMessage.INFORM) {
							System.out.println(myAgent.getLocalName() + "<=" + reply.getSender().getLocalName() + ":"
									+ reply.getContent());

							// UPdate the CONTRACT AND TIMESLOT
							contract = offerPrice + "|" + offerPenalty + "|" + offerKwh;
							timeSlot = offerTime;
							step = 4;
						} 
						break;
					}
				}
			}

	}

	@Override
	public boolean done() {
		// TODO Auto-generated method stub
		return (step == 4);
	}
}

private class PayService extends OneShotBehaviour {

	@Override
	public void action() {
		// TODO Auto-generated method stub
		if (contract != null && usage > 0) {
			String[] parts = contract.split("\\|");
			Double price = Double.parseDouble(parts[0]);
			Double penalty = Double.parseDouble(parts[1]);
			Double kwh = Double.parseDouble(parts[2]);
			Double pay = ((usage <= kwh) ? (usage * price) : ((kwh * price) + (usage - kwh) * penalty));
			// Update money
			money -= pay;
			ACLMessage informPay = new ACLMessage(ACLMessage.INFORM);
			informPay.setConversationId("PayService");
			informPay.setContent(pay.toString());
			informPay.addReceiver(lastRetailer);
			send(informPay);
			System.out.println(myAgent.getLocalName() + "=>" + lastRetailer.getLocalName() + ":Pay:$" + pay);

			// Update usage
			usage = 0.0;

		}

	}

}

private class AnswerStoreRequests extends CyclicBehaviour {

	@Override
	public void action() {
		// TODO Auto-generated method stub
		// Calculating Usage -= (power of all consuming device). for (AID conApp :
		MessageTemplate mt2 = MessageTemplate.MatchPerformative(ACLMessage.REQUEST);
		// mt = MessageTemplate.MatchSender(conApp);
		mt2 = MessageTemplate.and(MessageTemplate.MatchConversationId("RequestStore"),
				MessageTemplate.MatchProtocol(FIPANames.InteractionProtocol.FIPA_REQUEST));
		ACLMessage gotMess2 = receive(mt2);
		// System.out.println(gotMess2.getContent());
		if (gotMess2 != null) {
			store += Double.parseDouble(gotMess2.getContent());
			System.out.println(myAgent.getLocalName() + "<=" + gotMess2.getSender().getLocalName() + ":"
					+ gotMess2.getContent() + "Khw");


			// Inform success request
			ACLMessage rep = new ACLMessage(ACLMessage.INFORM);
			rep.setPerformative(ACLMessage.INFORM);
			rep.setConversationId("RequestStore");
			rep.setContent("Stored " + gotMess2.getContent() + "kwh successfully");
			System.out.println(myAgent.getLocalName() + "=>" + gotMess2.getSender().getLocalName() + ":"
					+ gotMess2.getContent() + "kwh  [stored= " + store + "]");
			myAgent.send(rep);
			if (timeSlot > 0)
				timeSlot -= 330;
		}

	}
}

private class AnswerConsumeRequests extends CyclicBehaviour {
	private ACLMessage gotMess;

	@Override
	public void action() {
		// TODO Auto-generated method stub

		// Calculating Usage += (power of all consuming device).
		if (timeSlot != 0) {

			MessageTemplate mt = MessageTemplate.MatchPerformative(ACLMessage.REQUEST);
			// mt = MessageTemplate.MatchSender(conApp);
			mt = MessageTemplate.and(MessageTemplate.MatchConversationId("RequestConsume"),
					MessageTemplate.MatchProtocol(FIPANames.InteractionProtocol.FIPA_REQUEST));
			gotMess = receive(mt);
			if (gotMess != null) {
				usage += Double.parseDouble(gotMess.getContent());
				System.out.println(myAgent.getLocalName() + "<=" + gotMess.getSender().getLocalName() + ":"
						+ gotMess.getContent() + "Khw.  [usage= " + usage + "]");

				ACLMessage reply = new ACLMessage(ACLMessage.INFORM);
				reply.setConversationId("RequestConsume");
				reply.setContent(gotMess.getContent());
				System.out.println(myAgent.getLocalName() + "=>" + gotMess.getSender().getLocalName() + ":Provided "
						+ gotMess.getContent() + "kwh");
				myAgent.send(reply);

				// every requests -timeSlot -330s
				if (timeSlot > 0)
					timeSlot -= 330;
			}

		}
	}
}

private class UpdatingRetailers extends CyclicBehaviour {

	@Override
	public void action() {
		// TODO Auto-generated method stub
		DFAgentDescription tp = new DFAgentDescription();
		ServiceDescription sd = new ServiceDescription();
		sd.setType("Retail");
		tp.addServices(sd);
		try {
			DFAgentDescription[] result = DFService.search(myAgent, tp);
			if (result != null) {
				// System.out.print("HA found consuming retailers: ");
				retailers = new AID[result.length];
				for (int i = 0; i < result.length; ++i) {
					retailers[i] = result[i].getName();
					// System.out.print(retailers[i].getLocalName() + " | ");
				}
				// System.out.println("...");
			}
		} catch (FIPAException fe) {
			fe.printStackTrace();
		}
	}

}

private class UpdatingApps extends CyclicBehaviour {

	@Override
	public void action() {
		// TODO Auto-generated method stub
		DFAgentDescription tp = new DFAgentDescription();
		ServiceDescription sd = new ServiceDescription();
		sd.setType("Consuming");
		tp.addServices(sd);
		try {
			DFAgentDescription[] result = DFService.search(myAgent, tp);
			if (result != null) {
				// System.out.print("HA found consuming apps: ");
				conApps = new AID[result.length];
				for (int i = 0; i < result.length; ++i) {
					conApps[i] = result[i].getName();
					// System.out.print(conApps[i].getLocalName() + " | ");
				}
				// System.out.println("...");
			}
		} catch (FIPAException fe) {
			fe.printStackTrace();
		}

		DFAgentDescription tp2 = new DFAgentDescription();
		ServiceDescription sd2 = new ServiceDescription();
		sd2.setType("Generating");
		tp2.addServices(sd2);
		try {
			DFAgentDescription[] result = DFService.search(myAgent, tp2);
			if (result != null) {
				// System.out.print("HA found generating apps: ");
				genApps = new AID[result.length];
				for (int i = 0; i < result.length; ++i) {
					genApps[i] = result[i].getName();
					// System.out.print(genApps[i].getLocalName() + " | ");
				}
				// System.out.println("...");
			}
		} catch (FIPAException fe) {
			fe.printStackTrace();
		}

	}

}

}
