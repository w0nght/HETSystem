package agents;

/**
 * @author HeiTung @ Asus on 26/10/2018
 */
import java.util.*;

import jade.domain.DFService;
import jade.domain.FIPAAgentManagement.DFAgentDescription;
import jade.domain.FIPAAgentManagement.ServiceDescription;
import jade.domain.FIPAException;
import jade.proto.AchieveREInitiator;
import jade.domain.FIPANames;

import jade.core.AID;
import jade.core.Agent;
import jade.core.behaviours.*;
import jade.lang.acl.*;

import org.json.simple.JSONObject;

public class HomeAgent extends Agent {
    private AID bestSeller; // the current retailer / last retailer bought from
    private List<AID> applianceList = new ArrayList<AID>(); // list of known appliances
    private List<AID> retailerList = new ArrayList<AID>(); // list of known retailers
    private HashMap<AID, Float> applianceUsage = new HashMap<AID, Float>(); // usage for each appliance
    private HashMap<AID, Float> retailerOffers = new HashMap<AID, Float>(); // list of retailer offers
    private int nResponders; // number of responders to expect
    private int maxPrice = 10; // per unit
    private int maxRounds = 3; // max number of rounds in negotiation
    private int roundCount = 1; // current round of negotiation
    private int currentRoundPower = 0; // a cached power value for negotiation

    public HomeAgent() {

    }

    protected void setup() {
        addBehaviour(new RegisterService());
        // First set-up message receiving behaviour
        CyclicBehaviour messageListeningBehaviour = new CyclicBehaviour(this) {
            public void action() {
                ACLMessage msg = receive();

                if (msg != null) {
                    if (!(msg.getPerformative() == ACLMessage.SUBSCRIBE || msg.getPerformative() == 50)) {
                        putBack(msg);
                        return;
                }
            }

                while (msg != null) {
                    if (msg.getPerformative() == 50) {
                        //System.out.println(getLocalName() + ": Received response " + msg.getContent() + " from " + msg.getSender().getLocalName());
                        applianceUsage.put(msg.getSender(), Float.valueOf(msg.getContent().replace("USAGE:", "")));
                    } else if (msg.getPerformative() == ACLMessage.SUBSCRIBE) {
                        if (msg.getContent().contains("appliance")) {
                            applianceList.add(msg.getSender());
                            applianceUsage.put(msg.getSender(), null);
                        } else {
                            retailerList.add(msg.getSender());
                        }
                        System.out.println(msg.getSender().getLocalName() + " subscribed to home");
                    }

                    msg = null;
                }
                //block();
            }
        };
        addBehaviour(messageListeningBehaviour);

        // every 1 second, grab the latest usage figures
        TickerBehaviour getUsage = new TickerBehaviour(this, 1000) {
            @Override
            public void onTick() {
                ACLMessage msg = new ACLMessage(ACLMessage.REQUEST);
                msg.setContent("GetUsage");

                for (AID appliance : applianceList)
                    msg.addReceiver(appliance);

                send(msg);
            }
        };
        addBehaviour(getUsage);

        // every 5 seconds, ask for offers from retailers
        TickerBehaviour negotiate = new TickerBehaviour(this, 5000) {
            @Override
            public void onTick() {
                // generate the request json
                JSONObject obj = new JSONObject();
                currentRoundPower = getPowerDemand();
                obj.put("usage", currentRoundPower);
                obj.put("round", roundCount);

                // prepare to call for proposal
                ACLMessage msg = new ACLMessage(ACLMessage.CFP);
                // set message protocol method
                msg.setProtocol(FIPANames.InteractionProtocol.FIPA_CONTRACT_NET);
                // set message timeout
                msg.setReplyByDate(new Date(System.currentTimeMillis() + 10000));

                // add all retailers
                for (AID retailer : retailerList)
                    msg.addReceiver(retailer);

                msg.setContent(obj.toJSONString());

                System.out.println(" ");

                addBehaviour(new RetailerNegotiate(myAgent, msg));
            }
        };
        addBehaviour(negotiate);
    }

    public int getPowerDemand() {
        // get current power usage
        int totalPower = 0;

        for (AID appliance : applianceList) {
            if (applianceUsage.containsKey(appliance) && applianceUsage.get(appliance) != null)
                totalPower += applianceUsage.get(appliance);
        }

        return totalPower;
    }

    /**
     * [Agent communication]
     * receive proposing to RAs [FIPA standard interaction protocols]
     */
    private class RetailerNegotiate extends AchieveREInitiator {
        public RetailerNegotiate(Agent a, ACLMessage msg) {
            super(a, msg);

        }

        protected void handleAgree(ACLMessage agree) {
            System.out.println(agree.getSender().getLocalName() + " has agreed to the request");
        }

        // Method to handle an inform message from responder
        protected void handleInform(ACLMessage inform) {
            System.out.println(inform.getSender().getLocalName() + " is offering $" + inform.getContent());

            retailerOffers.put(inform.getSender(), Float.valueOf(inform.getContent()));
        }

        // Method to handle a refuse message from responder
        protected void handleRefuse(ACLMessage refuse) {
            nResponders--;
        }

        // Method to handle a failure message (failure in delivering the message)
        protected void handleFailure(ACLMessage failure) {
            if (failure.getSender().equals(myAgent.getAMS())) {
                // FAILURE notification from the JADE runtime: the receiver (receiver does not exist)
                System.out.println(getLocalName() + ": Responder does not exist");
            } else {
                System.out.println(getLocalName() + ": " + failure.getSender().getName() + " failed to perform the requested action");
            }
        }

        // Method that is invoked when notifications have been received from all responders
        protected void handleAllResultNotifications(Vector notifications) {
            System.out.println("Round " + roundCount + " is on going");

            if (notifications.size() < retailerList.size()) {
                // Some responder didn't reply within the specified timeout
                System.out.println("Timeout expired: missing " + (nResponders - notifications.size()) + " responses");
            } else {
                System.out.println("Received notifications from every retailer.");
                System.out.println("Fetching for best offer...");

                // fetch for the best seller
                AID bestRetailer = retailerList.get(0);
                for (AID retailer : retailerList) {
                    // end if the retailerOffers map has the retailer value
                    if (!retailerOffers.containsKey(retailer))
                        continue;

                    System.out.println(retailer.getLocalName() + " offered $" + (retailerOffers.get(retailer) / currentRoundPower) + " per unit");

                    if (retailerOffers.get(bestRetailer) > retailerOffers.get(retailer))
                        bestRetailer = retailer;
                }

                // make sure the best offer is below the max
                if (retailerOffers.get(bestRetailer) / currentRoundPower > maxPrice && roundCount < maxRounds) {
                    System.out.println("none of exist responder is winning at this round!");
                    System.out.println("Attempt to renegotiate......");
                    roundCount++;

                    ACLMessage msg = new ACLMessage(ACLMessage.REQUEST);

                    for (AID retailer : retailerList)
                        msg.addReceiver(retailer)
                                ;
                    msg.setProtocol(FIPANames.InteractionProtocol.FIPA_REQUEST);
                    msg.setReplyByDate(new Date(System.currentTimeMillis() + 10000));

                    JSONObject obj = new JSONObject();
                    obj.put("usage", getPowerDemand());
                    obj.put("round", roundCount);
                    msg.setContent(obj.toJSONString());

                    System.out.println(" ");

                    addBehaviour(new RetailerNegotiate(myAgent, msg));

                    return;
                }

                System.out.println(bestRetailer.getLocalName() + " had the best offer!");
                bestSeller = bestRetailer;
                roundCount = 1;
            }
        }
    }

    /**
     * class to register home agent service
     * uses One SHot behaviour
     */
    private class RegisterService extends OneShotBehaviour {
        @Override
        public void action() {
            DFAgentDescription dfd = new DFAgentDescription();
            dfd.setName(getAID());
            ServiceDescription sd = new ServiceDescription();
            sd.setType("HomeAgent");
            sd.setName(getLocalName());
            dfd.addServices(sd);
            try {
                DFService.register(HomeAgent.this, dfd);
            }
            catch (FIPAException fe) {
                fe.printStackTrace();
            }
        }
    }
}