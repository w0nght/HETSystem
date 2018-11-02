package fml;

import jade.core.AID;
import jade.core.Agent;
import jade.core.behaviours.Behaviour;
import jade.core.behaviours.CyclicBehaviour;
import jade.core.behaviours.OneShotBehaviour;
import jade.domain.DFService;
import jade.domain.FIPAAgentManagement.*;
import jade.domain.FIPAException;
import jade.domain.FIPANames;
import jade.lang.acl.ACLMessage;
import jade.lang.acl.MessageTemplate;
import jade.proto.AchieveREResponder;
import jade.proto.ContractNetResponder;

/**
 * @author HeiTung @ Asus on 26/10/2018
 * @project EnergyTradingSystem
 * @package fm
 */
abstract class retailagent extends Agent {
    // Internal state variables
    boolean energyAvailable = true;
    int requestedPower = 0;
    int currentSystemTime = 0;

    public retailagent() {
    }

    @Override
    protected void setup() {
        // register retailer service
        addBehaviour(new RegisterService());
        System.out.println(getLocalName() + " await for request..");
        // start propose to how agent when receive
        addBehaviour(new ContractNetRespondBehaviour());
    }

    /**
     * class of how agent responds when receive cfp message
     * with cyclic behaviour
     */
    private class ContractNetRespondBehaviour extends CyclicBehaviour {
        @Override
        public void action() {
        // negotiation
            MessageTemplate template = MessageTemplate.and(
                    MessageTemplate.MatchProtocol(FIPANames.InteractionProtocol.FIPA_CONTRACT_NET),
                    MessageTemplate.MatchPerformative(ACLMessage.CFP) );
            ACLMessage cfpmsg = receive(template);

            if (cfpmsg != null) {
                if (cfpmsg.getPerformative() == ACLMessage.CFP) {
                    ACLMessage propose = cfpmsg.createReply();
                    // start proposing
                    if (energyAvailable){
                        // requested energy is available for sell
                        // reply the offer (per unit)
                        propose.setPerformative(ACLMessage.PROPOSE); // response to cfp
                        propose.setContent(String.valueOf(getOfferPrice(22)));
                    }
                    else {
                        // requested energy not available for sell
                        reply.setPerformative(ACLMessage.REQUEST); // response to cfp
                        reply.setContent(getLocalName() + "requested energy not available");
                    }
                }
                System.out.println(getLocalName() + "CFP Received");
                requestedPower = Integer.parseInt(cfp.getContent());
                propose.setPerformative(ACLMessage.PROPOSE);
                propose.setContent(String.valueOf(getOfferPrice(requestedPower)));

                ACLMessage inform = accept.createReply();
                //inform.setContent("propose accepted");
                inform.setPerformative(ACLMessage.ACCEPT_PROPOSAL);
                System.out.println("accept from " + accept.getSender().getLocalName());

                System.out.println(reject.getSender().getLocalName() + " is rejected to perform requested action.");

                ACLMessage reply = msg.createReply();

                // send reply
                send(reply);
            }

        }
    }


    /**
     * abstract method
     * calculate the offer price w/ different pricing strategies
     * @return offerPrice
     */
    abstract public double getOfferPrice(double usage) ;

    /**
     * class to register retail agent to DFService
     * with one shot behaviour
     *
     */
    private class RegisterService extends OneShotBehaviour {
        @Override
        public void action() {
            DFAgentDescription dfd = new DFAgentDescription();
            dfd.setName(getAID());
            ServiceDescription sd = new ServiceDescription();
            sd.setType("Retail");
            sd.setName(getLocalName());
            dfd.addServices(sd);
            try {
                DFService.register(myAgent, dfd);
            }
            catch (FIPAException fe) {
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
