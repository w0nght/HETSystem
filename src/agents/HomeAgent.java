package agents;

import java.util.Date;
import jade.core.AID;
import jade.core.Agent;
import jade.core.behaviours.CyclicBehaviour;
import jade.core.behaviours.OneShotBehaviour;
import jade.domain.DFService;
import jade.domain.FIPAException;
import jade.domain.FIPANames;
import jade.domain.FIPAAgentManagement.DFAgentDescription;
import jade.domain.FIPAAgentManagement.ServiceDescription;
import jade.lang.acl.ACLMessage;
import jade.lang.acl.MessageTemplate;

public class HomeAgent extends Agent {
    private Double usage = 5.5;                     // total Kwh appliance consumed
    private Double store = 0.0;                     // total Kwh appliance generated
    private String contract;                        // (SellingPrice|PenaltyPrice|timeSlot|Kwh)
    private Long timeSlot = (long) 0;               // Time in contract
    private Double maxBuy = 1.9;                    // maximum price to buy
    private Double minSell = 1.0;                   // minimum price to Sell
    private Double money;                           // credit to pay
    private AID[] retailers;                        // All searched retails
    private AID[] conApps;                          // All searched consuming apps
    private AID[] genApps;                          // All searched generating apps
    private AID lastRetailer;

    protected void setup() {
        // Printout a welcome message
        Object[] args = this.getArguments();
        if (args != null && args.length > 0) {
            money = Double.parseDouble((String) args[0]);
            System.out.println(getAID().getLocalName() + ": has $" + money + " is ready.");
        }
        addBehaviour(new RegisterService());
        addBehaviour(new UpdatingAppliance());
        addBehaviour(new UpdatingRetailers());

        addBehaviour(new AnswerConsumeRequests());
        addBehaviour(new LookingContract());

        addBehaviour(new PayService());
        addBehaviour(new AnswerStoreRequests());
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
        // Printout a dismissal message
        System.out.println(getAID().getLocalName() + " has been terminated.");
    }

    /**
     *
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
            } catch (FIPAException fe) {
                fe.printStackTrace();
            }
        }
    }

    /**
     * [Agent communication]
     * start negotiation w/ RAs
     * submit request to RAs [Contract Net interaction protocols]
     * ContractNetInitiator
     */
    private class LookingContract extends CyclicBehaviour {
        private int step = 0;
        private MessageTemplate mt; // The template to receive replies
        private AID bestSeller; // The agent who provides the best offer
        private Double bestPrice; // The best offered price
        private int repliesCount = 0; // The counter of replies from retailers
        private Double offerPrice;// offer price got from retailer
        private Double offerPenalty;
        private Double offerKwh;
        private long offerTime;
        private long t;

        @Override
        public void action() {
            if (timeSlot <= 0) {
                while (step < 4) {
                    if (step == 0) {
                        t=System.currentTimeMillis();
                        // Send the cfp to all sellers
                        // use the call for proposal performative
                        ACLMessage cfp = new ACLMessage(ACLMessage.CFP);
                        cfp.setConversationId("RetailerSelling");
                        // add cfp recipients
                        for (int i = 0; i < retailers.length; ++i) {
                            cfp.addReceiver(retailers[i]);
                            System.out.println(myAgent.getLocalName() + "=>" + retailers[i].getLocalName() + ":"
                                    + usage.toString() + "[wanna buy](CFP)");
                        }
                        // set content
                        cfp.setContent(usage.toString());
                        // set conversation's id
                        cfp.setConversationId("RetailerSelling");
                        // set timeout in three second
                        cfp.setReplyByDate(new Date(System.currentTimeMillis() + 3000));
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
                                System.out.println(myAgent.getLocalName()
                                        + "<=" + reply.getSender().getLocalName()
                                        + ":" + reply.getContent());

                                // if it is the best offer at present
                                if (bestSeller == null || offerPrice < bestPrice) {
                                    bestPrice = offerPrice;
                                    bestSeller = reply.getSender();
                                } else {
                                    // else, reject the proposal
                                    ACLMessage refuse = new ACLMessage(ACLMessage.REJECT_PROPOSAL);
                                    refuse.addReceiver(reply.getSender());
                                    refuse.setContent("Refuse");
                                    refuse.setConversationId("RetailerSelling");
                                    refuse.setReplyByDate(new Date(System.currentTimeMillis() + 3000));
                                    // Print out Sending Message
                                    System.out.println(myAgent.getLocalName() + "=>" + reply.getSender().getLocalName()
                                            + ":refused[found a better price](REJECT_PROPOSAL)");
                                    send(refuse);
                                    step = 1;
                                }

                                repliesCount++;

                                // if all replies has been received
                                // or if time out
                                if ((repliesCount >= retailers.length) ||
                                        ((t + System.currentTimeMillis() + 3000) >= System.currentTimeMillis())) {
                                    if (bestSeller != null) {

                                        if (maxBuy >= bestPrice) {
                                            step++;
                                        } else {
                                            ACLMessage refuse = new ACLMessage(ACLMessage.REJECT_PROPOSAL);
                                            refuse.addReceiver(bestSeller);
                                            refuse.setContent("Refuse");
                                            refuse.setConversationId("RetailerSelling");
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

    /**
     * class to pay the service to the best offered retailer of each round
     * if the negation is finished
     */
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
    /**
     * [Agent communication]
     * receive request [FIPA stadard REQUEST interaction protocols]
     */
    private class AnswerStoreRequests extends CyclicBehaviour {
        @Override
        public void action() {
            MessageTemplate mt2 = MessageTemplate.MatchPerformative(ACLMessage.REQUEST);
            mt2 = MessageTemplate.and(MessageTemplate.MatchConversationId("RequestStore"),
                    MessageTemplate.MatchProtocol(FIPANames.InteractionProtocol.FIPA_REQUEST));
            ACLMessage gotMess2 = receive(mt2);

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
        @Override
        public void action() {
            MessageTemplate mt = MessageTemplate.and(MessageTemplate.MatchConversationId("RequestConsume"),
                    MessageTemplate.MatchProtocol(FIPANames.InteractionProtocol.FIPA_REQUEST));
            ACLMessage request = receive(mt);
            if (request != null) {
                usage += Double.parseDouble(request.getContent());
                System.out.println(myAgent.getLocalName() + "<=" + request.getSender().getLocalName() + ":"
                        + request.getContent() + "Khw.  [usage= " + usage + "]");
                ACLMessage reply = request.createReply();
                if (timeSlot != 0) {
                    reply.setPerformative(ACLMessage.AGREE);
                    reply.setConversationId("RequestConsume");
                    reply.setContent(request.getContent());
                    System.out.println(myAgent.getLocalName() + "=>" + request.getSender().getLocalName() + ":Provided "
                            + request.getContent() + "kwh(agree)");

                    // every requests -timeSlot -330s
                    if (timeSlot > 0)
                        timeSlot -= 330;
                } else {
                    reply.setPerformative(ACLMessage.REFUSE);
                    reply.setConversationId("RequestConsume");
                    reply.setContent("I'm buying energy");
                    System.out.println(myAgent.getLocalName() + "=>" + request.getSender().getLocalName()
                            + ":I'm buying energy(refuse)");
                }
                myAgent.send(reply);
            }

        }
    }

    /**
     * class to update the retailers agents
     */
    private class UpdatingRetailers extends CyclicBehaviour {
        @Override
        public void action() {
            DFAgentDescription tp = new DFAgentDescription();
            ServiceDescription sd = new ServiceDescription();
            sd.setType("Retail");
            tp.addServices(sd);
            try {
                DFAgentDescription[] result = DFService.search(myAgent, tp);
                if (result != null) {
                    retailers = new AID[result.length];
                    for (int i = 0; i < result.length; ++i) {
                        retailers[i] = result[i].getName();
                    }
                }
            }
            catch (FIPAException fe) {
                fe.printStackTrace();
            }
        }

    }

    /**
     * class to update the appliance agents
     */
    private class UpdatingAppliance extends CyclicBehaviour {

        @Override
        public void action() {
            DFAgentDescription tp = new DFAgentDescription();
            ServiceDescription sd = new ServiceDescription();
            sd.setType("Consuming");
            tp.addServices(sd);
            try {
                DFAgentDescription[] result = DFService.search(myAgent, tp);
                if (result != null) {
                    conApps = new AID[result.length];
                    for (int i = 0; i < result.length; ++i) {
                        conApps[i] = result[i].getName();
                    }
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
                    genApps = new AID[result.length];
                    for (int i = 0; i < result.length; ++i) {
                        genApps[i] = result[i].getName();
                    }
                }
            } catch (FIPAException fe) {
                fe.printStackTrace();
            }

        }

    }

}

