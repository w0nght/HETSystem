package agents;

/**
 * @author HeiTung @ Asus on 26/10/2018
 */

import jade.core.Agent;
import jade.core.AID;
import jade.core.behaviours.OneShotBehaviour;
import jade.domain.DFService;
import jade.domain.FIPAAgentManagement.*;
import jade.domain.FIPAException;
import jade.lang.acl.ACLMessage;
import jade.lang.acl.MessageTemplate;
import jade.proto.AchieveREResponder;
import jade.domain.FIPANames;

import org.json.simple.JSONObject;
import org.json.simple.parser.ParseException;
import org.json.simple.parser.JSONParser;

abstract class RetailerAgent extends Agent{
    int minPrice = 8;               // minimum cost per unit
    int requestedPower = 0;            // the energy required
    int currentRequestRound = 0;       // the round of the last request // for calculate the discount

    public RetailerAgent() {
    }

    protected void setup()
    {
        ACLMessage msg = new ACLMessage(ACLMessage.SUBSCRIBE);
        msg.addReceiver(new AID("home", AID.ISLOCALNAME));
        msg.setContent("retailer");
        send(msg);
        // register service
        addBehaviour(new RegisterService());

        System.out.println(getLocalName() + " await for requests...");
        // listen only for messages matching the correct interaction protocol and performative
        MessageTemplate template = MessageTemplate.and(
                MessageTemplate.MatchProtocol(FIPANames.InteractionProtocol.FIPA_CONTRACT_NET),
                MessageTemplate.MatchPerformative(ACLMessage.CFP));

        // Add the AchieveREResponder behaviour which implements the responder role in a FIPA_REQUEST interaction protocol
        // The responder can either choose to agree to request or refuse request
        addBehaviour(new AchieveREResponder(this, template) {
            protected ACLMessage prepareResponse(ACLMessage request) throws NotUnderstoodException, RefuseException {
                JSONParser parser = new JSONParser();
                String s = request.getContent();

                try {
                    Object obj = parser.parse(s);
                    JSONObject jsonObj = (JSONObject)obj;
                    requestedPower = ((Long)jsonObj.get("usage")).intValue();
                    currentRequestRound = ((Long)jsonObj.get("round")).intValue();
                }	catch(ParseException pe){
                    System.err.println(s);
                }

                ACLMessage agree = request.createReply();
                agree.setPerformative(ACLMessage.AGREE);
                return agree;
            }

            // If the agent agreed to the request received, then it has to perform the associated action and return the result of the action
            protected ACLMessage prepareResultNotification(ACLMessage request, ACLMessage response) throws FailureException {
                ACLMessage inform = request.createReply();
                inform.setPerformative(ACLMessage.INFORM);
                // reply the offer (per unit)
                float offerPrice = getOfferPrice(requestedPower);
                // generate discount to attract buyer
                double discount = (currentRequestRound - 1) * (offerPrice * 0.005);
                double finalPrice = offerPrice - discount;


                // if the discounted price is lower
                if(finalPrice / requestedPower < minPrice)
                    // ignore the discount
                    finalPrice = offerPrice;

                inform.setContent(String.valueOf(finalPrice));
                return inform;
            }
        });
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
            sd.setType("retailer");
            sd.setName(getLocalName());
            dfd.addServices(sd);
            try {
                System.out.println(sd.getName() + " is now registered");
                DFService.register(RetailerAgent.this, dfd);
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

    /**
     * abstract method
     * calculate the offer price w/ different pricing strategies
     */
    abstract public float getOfferPrice(int usage);
}