package agents;

/**
 * @author HeiTung @ Asus on 26/10/2018
 */

import jade.core.Agent;
import jade.core.AID;
import jade.core.behaviours.CyclicBehaviour;
import jade.core.behaviours.OneShotBehaviour;
import jade.domain.DFService;
import jade.domain.FIPAAgentManagement.DFAgentDescription;
import jade.domain.FIPAAgentManagement.ServiceDescription;
import jade.domain.FIPAException;
import jade.domain.FIPANames;
import jade.lang.acl.ACLMessage;
import jade.lang.acl.MessageTemplate;

public class ApplianceAgent extends Agent {

    public ApplianceAgent() {
    }

    protected void setup() {
        ACLMessage msg = new ACLMessage(ACLMessage.SUBSCRIBE);
        msg.addReceiver(new AID("home", AID.ISLOCALNAME));
        msg.setContent("appliance");
        send(msg);
        // register service
        addBehaviour(new RegisterService());

        addBehaviour(new CyclicBehaviour(this) {
            public void action() {
                MessageTemplate template = MessageTemplate.and(
                        MessageTemplate.MatchProtocol(FIPANames.InteractionProtocol.FIPA_REQUEST),
                        MessageTemplate.MatchContent("GetUsage"));
                ACLMessage msg = receive();

                if (msg != null) {
                    // handle message
                    if(msg.getContent().contains("GetUsage"))
                    {
                        // Reply to message
                        ACLMessage reply = msg.createReply();
                        reply.setPerformative(50);
                        reply.setContent("USAGE:" + String.valueOf(getEstimatedConsumption()));

                        // Send reply
                        send(reply);
                    }
                }
            }
        });
    }

    /**
     * get the estimation of current consumption
     * @return estimateConsumption
     */
    public int getEstimatedConsumption() {
        int estimateConsumption = getActualConsumption() + 1;
        return estimateConsumption;
    }

    /**
     * get the actual energy consumption
     * @return actualConsumption
     */
    public int getActualConsumption() {
        int actualConsumption = (int)Math.round(Math.random() * 100);
        if (actualConsumption < 0) getActualConsumption();
        return actualConsumption;
    }

    /**
     * class of register appliance agent to DFService
     * with one shot behaviour
     */
    private class RegisterService extends OneShotBehaviour {
        @Override
        public void action() {
            DFAgentDescription dfd = new DFAgentDescription();
            dfd.setName(getAID());
            ServiceDescription sd = new ServiceDescription();
//            if (solar == true)
//                sd.setType("Generating");
//            else
            sd.setType("appliance");
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
     * method to take down - de-register the service
     * best practice
     */
    protected void takeDown() {
        try {
            DFService.deregister(this);
        }
        catch (FIPAException fe) {
            fe.printStackTrace();
        }
        System.out.println(getAID() + " has been terminated.");
    }
}
