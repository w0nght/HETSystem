package agents;

import jade.core.AID;
import jade.core.Agent;
import jade.core.behaviours.CyclicBehaviour;
import jade.core.behaviours.OneShotBehaviour;
import jade.core.behaviours.SequentialBehaviour;
import jade.domain.DFService;
import jade.domain.FIPAException;
import jade.domain.FIPANames;
import jade.domain.FIPAAgentManagement.DFAgentDescription;
import jade.domain.FIPAAgentManagement.ServiceDescription;
import jade.lang.acl.ACLMessage;
import jade.lang.acl.MessageTemplate;

public class ApplianceAgent extends Agent {
    private Double power;                       // Kwh
    private Boolean solar;                      // is the App solar? (1:solar, other: consume device)

    private AID homeAgent;

    protected void setup() {
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
             * Generate for solar app & Consume for normal appliances
             */

            SequentialBehaviour seq = new SequentialBehaviour();

            // register service
            seq.addSubBehaviour(new RegisterService());
            // look for home agent
            seq.addSubBehaviour(new FindHomeAgent());
            // respond with home agent's request
            // return the energy comsumption
            seq.addSubBehaviour(new RequestRespondBehaviour());
            addBehaviour(seq);
        }
    }

    /**
     * method to take down - de-register the service
     * best practice
     */
    protected void takeDown() {
        try {
            DFService.deregister(this);
        } catch (FIPAException fe) {
            fe.printStackTrace();
        }
        System.out.println("Appliance: " + getAID().getLocalName() + " has been terminated.");
    }

    /**
     * class to register retail agent to DFService
     * with one shot behaviour
     */
    private class RegisterService extends OneShotBehaviour {
        @Override
        public void action() {
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
                DFService.register(ApplianceAgent.this, dfd);
            } catch (FIPAException fe) {
                fe.printStackTrace();
            }
        }
    }

    /**
     * class to look for home agent
     * with one shot behaviour
     */
    private class FindHomeAgent extends OneShotBehaviour {
        @Override
        public void action() {
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

    /**
     * class of how agent responds when receive request message
     * with cyclic behaviour
     */
    private class RequestRespondBehaviour extends CyclicBehaviour {
        private int step = 0;

        @Override
        public void action() {
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
                    // reply with the consumed energy
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
