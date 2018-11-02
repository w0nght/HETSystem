package fml;

import jade.core.AID;
import jade.core.Agent;
import jade.core.behaviours.Behaviour;
import jade.core.behaviours.CyclicBehaviour;
import jade.core.behaviours.OneShotBehaviour;
import jade.core.behaviours.TickerBehaviour;
import jade.domain.AMSService;
import jade.domain.DFService;
import jade.domain.FIPAAgentManagement.AMSAgentDescription;
import jade.domain.FIPAAgentManagement.DFAgentDescription;
import jade.domain.FIPAAgentManagement.SearchConstraints;
import jade.domain.FIPAAgentManagement.ServiceDescription;
import jade.domain.FIPAException;
import jade.domain.FIPANames;
import jade.lang.acl.ACLMessage;
import jade.lang.acl.MessageTemplate;
import jade.proto.ContractNetInitiator;

import java.lang.reflect.Array;
import java.util.*;

/**
 * @author HeiTung @ Asus on 23/10/2018
 * @project EnergyTradingSystem
 * @package fm
 */
public class homeagent extends Agent {
    // Internal state variables
    //private ArrayList<String> applianceList = new ArrayList<String>();     // list of subscribed appliances
    //private ArrayList<String> retailerList = new ArrayList<String>();     // list of subscribed retailers
    private AID[] applianceList;
    private AID[] retailerList;

    private HashMap<String, Float> applianceConsumed = new HashMap<String, Float>();     // map of appliance with its current energy consumption
    private HashMap<String, Float> retailerOffers = new HashMap<String, Float>();     // map of retailer with its current offer
    private int usage = 0;

    private int nResponders;

    public homeagent() {
    }

    protected void setup() {
        // create a new service description for home agent
        ServiceDescription sd = new ServiceDescription();
        sd.setType("home");
        sd.setName(getLocalName());
        // register to DF service
        //register(sd);

        // use DFService to search for all retailers and appliances
        //AID[] applianceList = lookupService(sd.getType(retailer));
        //AID[] retailerList = lookupService(retailer);


        //addBehaviour(new BroadcastMessageBehaviour());
        for (int i = 0; i < retailerList.length ; i++)
        {
            System.out.println("retailer list array :" + retailerList[i].getName());
        }
        for (int i = 0; i < applianceList.length ; i++)
        {
            System.out.println("applianceL  list array :" + applianceList[i].getName());
        }

        //retailerOffers
        // get power demand from all appliance
        //getPowerDemand();
        // schedule the request with all retailer every 5 seconds
        addBehaviour(new TickerBehaviour(this, 5000) {
            @Override
            protected void onTick() {
                addBehaviour(new PerformNegotiation());
            }
        });
        //startNegotiate(retailerList);

    }
    // for testing purpose
    private void listener() {
        // set up message receiving behaviour
        OneShotBehaviour getSubscribers = new OneShotBehaviour(this) {
            public void action() {
                System.out.println("runngin getSubscribers");
                MessageTemplate template = MessageTemplate.MatchPerformative(ACLMessage.SUBSCRIBE);
                ACLMessage msg = receive();
                //ACLMessage msg = receive();
                if (msg != null && !(msg.getPerformative() == ACLMessage.INFORM || msg.getPerformative() == ACLMessage.SUBSCRIBE)) {
                    System.out.println("msg is null / non inform or sub");
                    putBack(msg);
                    return;
                }
                if (msg != null) {
                    if (msg.getPerformative() == ACLMessage.SUBSCRIBE) {
                        if (msg.getContent().contains("appliance")) {
                            System.out.println("msg SUBSCRIBE  - appliance");
                            // add the subscribed appliance to the array list
                            //applianceList.add(msg.getSender().getName());
                            // put appliance as key to the hash map, with null as the value
                            applianceConsumed.put(msg.getSender().getLocalName(), null);
                        }
                        else if (msg.getContent().contains("retailer")) {
                            //retailerList.add(msg.getSender().getName());
                            retailerOffers.put(msg.getSender().getLocalName(), null);
                        }
                    }
                    else if (msg.getPerformative() == ACLMessage.INFORM) {
//                        if (applianceList.contains(msg.getSender())) {
//                            applianceConsumed.put(msg.getSender().getLocalName(), Float.valueOf(msg.getContent()));
//                        }
                    }
                }
            }
        };
        addBehaviour(getSubscribers);
    }

    /**
     * [Agent communication]
     * receive request from AAs [FIPA standard interaction protocols]
     * forecast energy demands for next-time period,
     * (based on historical energy consumption profiles,
     * and using appropriate prediction algorithms)
     * get the value and return to RA
     */
    private void getPowerDemand() {
        // return usage
        ACLMessage request = new ACLMessage(ACLMessage.REQUEST);
        // add request message recipients
        for (int i = 0; i < applianceList.length; i++) {
            request.addReceiver(applianceList[i]);
        }
        // set interaction protocol
        request.setProtocol(FIPANames.InteractionProtocol.FIPA_REQUEST);
        // set timeout in one second
        request.setReplyByDate(new Date(System.currentTimeMillis() + 1000));
        // set conversation's id
        request.setConversationId("energy-consumption");
        // set content
        request.setContent("please provide your energy consumption");
        // send request
        send(request);

        MessageTemplate mt = MessageTemplate.and(MessageTemplate.MatchConversationId("energy-request"), MessageTemplate.MatchInReplyTo(request.getReplyWith()));



    }

    /**
     * [Agent communication]
     * start negotiation w/ RAs
     * submit request to RAs [Contract Net interaction protocols]
     * ContractNetInitiator
     */
    private class PerformNegotiation extends Behaviour {
        private AID bestSeller;             // the agent who provides the best offer
        private int bestPrice;              // the best offered price
        private int step = 0;
        private int repliesCount = 0;       // counter of replies from retailer agents
        private MessageTemplate mt;         // message template to receive replies

        public void action() {
            switch (step) {
                case 0:
                    // initiates negotiation
                    System.out.println("Calling for proposal with " + nResponders + " responders.");
                    // use the call for proposal performative
                    ACLMessage cfpMessage = new ACLMessage(ACLMessage.CFP);
                    // add cfp recipients
                    for (int i = 0; i < retailerList.length; i++) {
                        cfpMessage.addReceiver(retailerList[i]);
                    }
                    // set interaction protocol
                    cfpMessage.setProtocol(FIPANames.InteractionProtocol.FIPA_CONTRACT_NET);
                    // set timeout in one second
                    cfpMessage.setReplyByDate(new Date(System.currentTimeMillis() + 1000));
                    // set conversation's id
                    cfpMessage.setConversationId("energy-request");
                    // set content
                    cfpMessage.setContent(usage);
                    // call for propose
                    send(cfpMessage);
                    //
                    mt = MessageTemplate.and(MessageTemplate.MatchConversationId("energy-request"), MessageTemplate.MatchInReplyTo(cfpMessage.getReplyWith()));
                    step =1;
                    break;

                case 1:
                    // Evaluate the proposal
                    ACLMessage reply = receive(mt);
                    if (reply != null) {
                        // reply received
                        if (reply.getPerformative() == ACLMessage.PROPOSE) {
                            // get offer
                            int price = Integer.parseInt(reply.getContent());
                            // if it is the best offer at present
                            if (bestSeller == null || price < bestPrice) {
                                bestPrice = price;      // update its as the best price
                                bestSeller = reply.getSender();     // reply
                                ACLMessage accept = new ACLMessage(ACLMessage.ACCEPT_PROPOSAL);
                                accept.addReceiver(bestSeller);
                                // reply to the best seller that we accepted the proposal
                                send(accept);
                            }
                            else {
                                ACLMessage reject = new ACLMessage(ACLMessage.REJECT_PROPOSAL);
                                for (int i = 0; i < retailerList.length; i++)
                                {
                                    if (retailerList[i].getLocalName() != bestSeller.getLocalName()) {
                                        System.out.println("rejecting " + i + "/ " + retailerList[i]);
                                        reject.addReceiver(retailerList[i]);
                                    }
                                }
                                // reply to the best seller that we accepted the proposal
                                send(reject);
                            }
                        }
                        repliesCount++;
                        // if all replies has been received
                        if (repliesCount >= retailerList.length) {
                            step = 2;
                        }
                    }
                    else {
                        System.out.println("ERROR: Specified service type not exist!");
                        block();
                    }
                    break;

                case 2:

                    step = 3;
                    break;
                case 3:
                    // receive the purchase order reply
                    reply = receive(mt);
                    if (reply != null) {
                        // purchase order reply received
                        if (reply.getPerformative() == ACLMessage.INFORM) {
                            System.out.println("Successfully purchased " + usage + "unit of energy");
                            System.out.println("Price is $" + bestPrice);
                            //myAgent.doDelete();
                        }
                        step = 4;
                    }
                    else {
                        block();
                    }
                    break;
            }
        }

        public boolean done() {
            return ((step == 2 && bestSeller == null || step == 4));
        }
    }

    /**
     * class that do lookup the DFService for a specified type of service
     * using One Shot behaviour
     */
    private class SearchForServices extends OneShotBehaviour {
        public void action() {
            // parameters
            DFAgentDescription dfdRetail = new DFAgentDescription();
            ServiceDescription sdRetail = new ServiceDescription();
            sdRetail.setType("retailer");
            dfdRetail.addServices(sdRetail);

            DFAgentDescription dfdAppliance = new DFAgentDescription();
            ServiceDescription sdAppliance = new ServiceDescription();
            sdAppliance.setType("appliance");
            dfdAppliance.addServices(sdAppliance);
            // lookup retail service
            try {
                DFAgentDescription[] retailResult = DFService.search(myAgent, dfdRetail);
                DFAgentDescription[] applianceResult = DFService.search(myAgent, dfdAppliance);
                if (retailResult != null) {
                    retailerList = new AID[retailResult.length];
                    for (int i = 0; i < retailResult.length; i++) {
                        retailerList[i] = retailResult[i].getName();
                    }
                } else if (applianceResult != null) {
                    applianceList = new AID[applianceResult.length];
                    for (int i = 0; i < applianceResult.length; i++) {
                        applianceList[i] = applianceResult[i].getName();
                    }
                }
                System.out.println("retailer list :" + retailerList);
            }
            catch (FIPAException fe) {
                System.out.println(getLocalName() + ": Unable to reach for " + dfdRetail + "service");
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