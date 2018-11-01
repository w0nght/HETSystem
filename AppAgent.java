package HETS;

import java.util.Date;

import jade.core.AID;
import jade.core.Agent;
import jade.core.behaviours.Behaviour;
import jade.core.behaviours.CyclicBehaviour;
import jade.core.behaviours.OneShotBehaviour;
import jade.core.behaviours.SequentialBehaviour;
import jade.core.behaviours.Behaviour;
import jade.domain.DFService;
import jade.domain.FIPAException;
import jade.domain.FIPANames;
import jade.domain.FIPAAgentManagement.DFAgentDescription;
import jade.domain.FIPAAgentManagement.ServiceDescription;
import jade.lang.acl.ACLMessage;
import jade.lang.acl.MessageTemplate;

public class AppAgent extends Agent {
	/**
	 *  
	 */

	private Double power; // Kwh
	private Boolean solar; // is the App solar? (1:solar, other: consume device)

	private AID homeAgent;

	protected void setup() {
		// Printout a welcome message and the power
		// System.out.println("Availabe appliance: " + getAID().getName());
		Object[] args = this.getArguments();
		if (args != null && args.length > 0) {
			power = Double.parseDouble((String) args[0]);
			solar = Boolean.parseBoolean((String) args[1]);
			if (solar == true)
				System.out.println(getAID().getLocalName() + " will generate " + power + " Kwh, being ready.");
			else
				System.out.println(getAID().getLocalName() + " will consume " + power + " Kwhbeing ready.");

		} else {
			System.out.println(getAID().getLocalName() + " cannot be used.");
		}

		if (power != null && solar != null) {
			/**
			 * Register the "consume" or "generate" request in the yellow pages Set type
			 * Generate for solar app & Consume for normal apps
			 */

			SequentialBehaviour seq = new SequentialBehaviour();

			RegisterService reg = new RegisterService();
			FindHomeAgent findHA = new FindHomeAgent();
			RequestPerformer req = new RequestPerformer();

			seq.addSubBehaviour(reg);
			seq.addSubBehaviour(findHA);
			seq.addSubBehaviour(req);
			addBehaviour(seq);
		}

	}

	protected void takeDown() {
		try {
			DFService.deregister(this);
		} catch (FIPAException fe) {
			fe.printStackTrace();
		}
		// Printout a dismissal message
		System.out.println("Appliance: " + getAID().getLocalName() + " has been terminated.");
	}

	private class RegisterService extends OneShotBehaviour {

		@Override
		public void action() {
			// TODO Auto-generated method stub
			DFAgentDescription dfd = new DFAgentDescription();
			dfd.setName(getAID());
			ServiceDescription sd = new ServiceDescription();
			if (solar == true)
				sd.setType("Generating");
			else
				sd.setType("Consuming");
			sd.setName(getLocalName());
			dfd.addServices(sd);
			try {
				DFService.register(AppAgent.this, dfd);
			} catch (FIPAException fe) {
				fe.printStackTrace();
			}
		}

	}

	private class FindHomeAgent extends OneShotBehaviour {

		@Override
		public void action() {
			// TODO Auto-generated method stub
			// Find the home agent
			DFAgentDescription dfd = new DFAgentDescription();
			ServiceDescription sd = new ServiceDescription();
			sd.setType("HomeAgent");
			dfd.addServices(sd);
			try {
				DFAgentDescription[] searchResult = DFService.search(myAgent, dfd);
				if (searchResult != null)
					homeAgent = searchResult[0].getName(); // This search result is home agent
			} catch (FIPAException fe) {
				fe.printStackTrace();
			}

		}

	}

	private class RequestPerformer extends CyclicBehaviour {
		private int step = 0;

		@Override
		public void action() {
			// TODO Auto-generated method stub
			while (step < 2) {
				block(1000);
				if (step == 0) {
					// Send request to HA
					ACLMessage request = new ACLMessage(ACLMessage.REQUEST);
					request.setProtocol(FIPANames.InteractionProtocol.FIPA_REQUEST);
					if (solar == true)
						request.setConversationId("RequestStore");
					else
						request.setConversationId("RequestConsume");
					// request.setReplyByDate(new Date(System.currentTimeMillis() + 10000)); // We
					// want to receive a reply in 10 secs
					request.setContent(power.toString());
					request.addReceiver(homeAgent);
					send(request);
					System.out.println(myAgent.getLocalName() + "==>" + homeAgent.getLocalName() + ":"
							+ power.toString() + "(REQUEST)");
					step++;
				}
				if (step == 1) {
					MessageTemplate tp = MessageTemplate.or(MessageTemplate.MatchPerformative(ACLMessage.AGREE),
							MessageTemplate.MatchPerformative(ACLMessage.REFUSE));
					tp = MessageTemplate.MatchSender(homeAgent);
					tp = MessageTemplate.MatchConversationId("RequestConsume");
					ACLMessage reply = blockingReceive(tp);
					if (reply != null) {
						System.out.println(myAgent.getLocalName() + "<=" + reply.getSender().getLocalName() + ":"
								+ reply.getContent());
						step = 2;
					}

				}
			}
		}

	}

}