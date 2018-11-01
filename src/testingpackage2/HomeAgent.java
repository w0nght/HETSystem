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
	private Double usage = 5.5; // (total Kwh Apps consumed)
	private Double store = 0.0; // (total Kwh Apps generated)
	private String contract; // (SellingPrice|PenaltyPrice|timeSlot|Kwh)
	private Long timeSlot = (long) 0; // Time in contract
	private Double maxBuy = 1.9; // maximum price to buy
	private Double minSell = 1.0; // minimum price to Sell
	private Double money; // credit to pay
	private long timeAllow = 3000; // time allow to retailer response
	private AID[] retailers; // All searched retails
	private AID[] conApps; // All searched consuming apps
	private AID[] genApps; // All searched generating apps
	private AID lastRetailer;

	protected void setup() {
		// Printout a welcome message
		Object[] args = this.getArguments();
		if (args != null && args.length > 0) {
			money = Double.parseDouble((String) args[0]);
			System.out.println(getAID().getLocalName() + ": has $" + money + " is ready.");
		}

		// SequentialBehaviour seq = new SequentialBehaviour();
		// ParallelBehaviour pb = new ParallelBehaviour(ParallelBehaviour.WHEN_ANY);

		RegisterService reg = new RegisterService();
		UpdatingApps apps = new UpdatingApps();
		UpdatingRetailers res = new UpdatingRetailers();
		AnswerStoreRequests sto = new AnswerStoreRequests();
		LookingContract loo = new LookingContract();
		AnswerConsumeRequests con = new AnswerConsumeRequests();
		

		addBehaviour(new RegisterService());
		addBehaviour(new UpdatingApps());
		addBehaviour(new UpdatingRetailers());
		addBehaviour(new AnswerConsumeRequests());
		addBehaviour(new LookingContract());

		addBehaviour(new PayService());
		addBehaviour(new AnswerStoreRequests());

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

	private class LookingContract extends CyclicBehaviour {
		private int step = 0;
		private MessageTemplate mt; // The template to receive replies
		private AID bestSeller; // The agent who provides the best offer
		private Double bestPrice; // The best offered price
		private int repCount = 0; // The counter of replies from retailers
		private long timeAllow; // timeAllow to get offer from retailers after send cfp
		private Double offerPrice;// offer price got from retailer
		private Double offerPenalty;
		private Double offerKwh;
		private long offerTime;

		@Override
		public void action() {
			// TODO Auto-generated method stub
			if (timeSlot <= 0) {
				while (step < 4) {
					if (step == 0) {
						// Send the cfp to all sellers
						ACLMessage cfp = new ACLMessage(ACLMessage.CFP);
						cfp.setConversationId("RetailerSelling");
						for (int i = 0; i < retailers.length; ++i) {
							cfp.addReceiver(retailers[i]);
							System.out.println(myAgent.getLocalName() + "=>" + retailers[i].getLocalName() + ":"
									+ usage.toString() + "[wanna buy](CFP)");
						}
						cfp.setContent(usage.toString());
						cfp.setConversationId("RetailerSelling");
						cfp.setReplyByDate(new Date(timeAllow));
						myAgent.send(cfp);

						step++;

						// Prepare the template to get proposals
						mt = MessageTemplate.and(MessageTemplate.MatchConversationId("RetailerSelling"),
								MessageTemplate.MatchInReplyTo(cfp.getReplyWith()));
					}

					if (step == 1) {
						// Receive all proposals/refusals from retailers
						ACLMessage reply = receive();

						if (reply != null) {
							// Reply received
							if (reply.getPerformative() == ACLMessage.PROPOSE) {
								// Decompose the String of Contract Offer
								String[] parts = reply.getContent().split("\\|");
								offerPrice = Double.parseDouble(parts[0].toString());
								offerPenalty = Double.parseDouble(parts[1]);
								offerTime = Long.parseLong(parts[2].toString());
								offerKwh = Double.parseDouble(parts[3].toString());
								// Print out received Message
								System.out.println(myAgent.getLocalName() + "<=" + reply.getSender().getLocalName()
										+ ":" + reply.getContent());

								if (bestSeller == null || offerPrice < bestPrice) {
									// This is the best offer at present
									bestPrice = offerPrice;
									bestSeller = reply.getSender();
								} else {
									ACLMessage refuse = new ACLMessage(ACLMessage.REJECT_PROPOSAL);
									refuse.addReceiver(reply.getSender());
									refuse.setContent("Refuse");
									refuse.setConversationId("RetailerSelling");
									// refuse.setReplyWith("I'll refuse" + System.currentTimeMillis());
									// Print out Sending Message
									System.out.println(myAgent.getLocalName() + "=>" + reply.getSender().getLocalName()
											+ ":refused[found a better price](REJECT_PROPOSAL)");
									send(refuse);
									step = 1;
								}

								repCount++;
								if ((repCount >= retailers.length) || (timeAllow >= System.currentTimeMillis())) {
									// If ( received all retailers or over timeAllow)
									if (bestSeller != null) {

										if (maxBuy >= bestPrice) { // If bestprice <= maxprice, next else refuse
											step++;
										} else {
											ACLMessage refuse = new ACLMessage(ACLMessage.REJECT_PROPOSAL);
											refuse.addReceiver(bestSeller);
											refuse.setContent("Refuse");
											refuse.setConversationId("RetailerSelling");
											// refuse.setReplyWith("I'll refuse" + System.currentTimeMillis());
											System.out.println(myAgent.getLocalName() + "=>" + bestSeller.getLocalName()
													+ ":Refuse[Over Max Buy Price](REJECT_PROPOSAL)");
											send(refuse);
											step = 0;
										}
									} else
										step = 0;
								}
							}
						}
					}
					if (step == 2) {
						// Send the purchase order to the retailer that provided the best offer
						ACLMessage accept = new ACLMessage(ACLMessage.ACCEPT_PROPOSAL);
						accept.addReceiver(bestSeller);
						accept.setContent("Accepted");
						accept.setConversationId("RetailerSelling");
						accept.setReplyWith("I'll buy" + System.currentTimeMillis());
						myAgent.send(accept);
						System.out.println(myAgent.getLocalName() + "=>" + bestSeller.getLocalName()
								+ ":Accepted(ACCEPT_PROPOSAL)");
						// Prepare the template to get the purchase reply from best retailer
						mt = MessageTemplate.and(MessageTemplate.MatchConversationId("RetailerSelling"),
								MessageTemplate.MatchInReplyTo(accept.getReplyWith()));
						step++;
					}
					if (step == 3) {
						// Receive provided service
						ACLMessage reply = myAgent.receive(mt);
						if (reply != null) {
							if (reply.getPerformative() == ACLMessage.INFORM) {
								System.out.println(myAgent.getLocalName() + "<=" + reply.getSender().getLocalName()
										+ ":" + reply.getContent());

								// UPdate the CONTRACT AND TIMESLOT
								contract = offerPrice + "|" + offerPenalty + "|" + offerKwh;
								timeSlot = offerTime;
								// Update lastRetailer to Pay
								lastRetailer = reply.getSender();
								step = 4;
							}
						}

					}
				}
			}
		}

	}

	private class PayService extends CyclicBehaviour {

		@Override
		public void action() {
			// TODO Auto-generated method stub
			block(1000);
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
				System.out.println(myAgent.getLocalName() + "=>" + lastRetailer.getLocalName() + ":Pay:$" + pay);
				send(informPay);

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
				ACLMessage rep = gotMess2.createReply();
				rep.setPerformative(ACLMessage.AGREE);
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

			MessageTemplate mt = MessageTemplate.MatchPerformative(ACLMessage.REQUEST);
			// mt = MessageTemplate.MatchSender(conApp);
			mt = MessageTemplate.and(MessageTemplate.MatchConversationId("RequestConsume"),
					MessageTemplate.MatchProtocol(FIPANames.InteractionProtocol.FIPA_REQUEST));
			gotMess = receive(mt);
			if (gotMess != null) {
				usage += Double.parseDouble(gotMess.getContent());
				System.out.println(myAgent.getLocalName() + "<=" + gotMess.getSender().getLocalName() + ":"
						+ gotMess.getContent() + "Khw.  [usage= " + usage + "]");
				ACLMessage reply = gotMess.createReply();
				if (timeSlot != 0) {
					reply.setPerformative(ACLMessage.AGREE);
					reply.setConversationId("RequestConsume");
					reply.setContent(gotMess.getContent());
					System.out.println(myAgent.getLocalName() + "=>" + gotMess.getSender().getLocalName() + ":Provided "
							+ gotMess.getContent() + "kwh(agree)");

					// every requests -timeSlot -330s
					if (timeSlot > 0)
						timeSlot -= 330;
				} else {
					reply.setPerformative(ACLMessage.REFUSE);
					reply.setConversationId("RequestConsume");
					reply.setContent("I'm buying energy");
					System.out.println(myAgent.getLocalName() + "=>" + gotMess.getSender().getLocalName()
							+ ":I'm buying energy(refuse)");
				}
				myAgent.send(reply);
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
