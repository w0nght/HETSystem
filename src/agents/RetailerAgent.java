package agents;

import jade.core.Agent;
import jade.core.behaviours.CyclicBehaviour;
import jade.core.behaviours.OneShotBehaviour;
import jade.core.behaviours.TickerBehaviour;
import jade.domain.DFService;
import jade.domain.FIPAException;
import jade.domain.FIPAAgentManagement.DFAgentDescription;
import jade.domain.FIPAAgentManagement.ServiceDescription;
import jade.lang.acl.ACLMessage;
import jade.lang.acl.MessageTemplate;

public class RetailerAgent extends Agent {
    private Double sellPrice = 2.0;                         // how much Kwh
    private Double buyPrice;                                // how much Kwh
    private Double penaltyPrice;                            // Penalty price to buy Kwh

    private Long timeContract = (long) 3000;                // months in contracts
    private Double sum = 0.0;                               // Total to get paid
    private double demand = 0;                              // reduce if home agent do not need energy

    private PricingStrategy pricing;
    private TimeStrategy timeResponse;                      // time delay to give offer for HA

    public Boolean Delay(long t) {
        long t1 = System.currentTimeMillis();
        long t2 = System.currentTimeMillis();
        while ((t2 < (t + t1))) {
            t2 = System.currentTimeMillis();
        }
        return true;
    }

    protected void setup() {
        // Printout a welcome message

        Object[] args = this.getArguments();
        if (args != null && args.length > 0) {
            buyPrice = Double.parseDouble((String) args[0]);
            penaltyPrice = Double.parseDouble((String) args[1]);
            String pricingStrategySelection = (String) args[2];
            if (pricingStrategySelection.equalsIgnoreCase("demand")) {
                pricing = new DemandPricingStrategy();
                timeResponse = new Quick();
            } else if (pricingStrategySelection.equalsIgnoreCase("yearly")) {
                timeContract = (long) 12000;
                pricing = new YearlyPricingStrategy();
                timeResponse = new Slow();
            } else {
                pricing = new PricingStrategyFixed();
                timeResponse = new Quick();
            }

            System.out.println(getAID().getLocalName() + "(Sell:$" + pricing.getPrice() + ", Buy:$" + buyPrice
                    + ", Penalty:$" + penaltyPrice + ") is ready.");
        } else {
            System.out.println("Retail appliance: " + getAID().getLocalName() + " cannot be used.");
        }

        // register service to home agent
        addBehaviour(new RegisterService());
        addBehaviour(new ProvideTheSellingEnergy());
        addBehaviour(new OfferingContract());
        addBehaviour(new GetPayService());
    }

    protected void takeDown() {
        // Printout a dismissal message
        try {
            DFService.deregister(this);
        } catch (FIPAException fe) {
            fe.printStackTrace();
        }
        System.out.println("Retailer: " + getAID().getName() + " has been terminated.");
    }

    private class DemandTicker extends TickerBehaviour {

        public DemandTicker(Agent a) {
            super(a, 5000);
            // TODO Auto-generated constructor stub
        }

        @Override
        protected void onTick() {
            if (demand > 0)
                demand -= 0.01;
            // TODO Auto-generated method stub

        }

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
            sd.setType("Retail");
            sd.setName(getLocalName());
            dfd.addServices(sd);
            try {
                DFService.register(RetailerAgent.this, dfd);
            } catch (FIPAException fe) {
                fe.printStackTrace();
            }

        }

    }

    /**
     * class to register retail agent to DFService
     * with one shot behaviour
     */
    private class GetPayService extends CyclicBehaviour {
        @Override
        public void action() {
            MessageTemplate mt = MessageTemplate.and(MessageTemplate.MatchPerformative(ACLMessage.INFORM),
                    MessageTemplate.MatchConversationId("PayService"));

            ACLMessage gotMess = receive(mt);
            if (gotMess != null) {
                sum += Double.parseDouble(gotMess.getContent());
                System.out.println(myAgent.getLocalName() + "<=" + gotMess.getSender().getLocalName() + ":$"
                        + gotMess.getContent() + "[pay]sum:" + sum);
            }

        }
    }

    private class ProvideTheSellingEnergy extends CyclicBehaviour {
        @Override
        public void action() {

            MessageTemplate mt = MessageTemplate.and(MessageTemplate.MatchPerformative(ACLMessage.ACCEPT_PROPOSAL),
                    MessageTemplate.MatchConversationId("RetailerSelling"));
            ACLMessage msg = myAgent.receive(mt);
            if (msg != null) {
                System.out.println(
                        myAgent.getLocalName() + "<=" + msg.getSender().getLocalName() + ":" + msg.getContent());
                // ACCEPT_PROPOSAL Message received. Inform to the HA
                ACLMessage reply = msg.createReply();
                if (reply != null) {
                    reply.setPerformative(ACLMessage.INFORM);
                    reply.setConversationId("RetailerSelling");
                    reply.setContent("I provided service");
                    System.out.println(myAgent.getLocalName() + "=>" + msg.getSender().getLocalName()
                            + ":I provided service (INFORM)");
                    demand += 0.2;
                } else {
                    // The requested book has been sold to another buyer in the meanwhile .
                    reply.setPerformative(ACLMessage.FAILURE);
                    reply.setContent("I cannot provide the service");
                    System.out.println(myAgent.getLocalName() + "=>" + msg.getSender().getLocalName()
                            + ":I cannot provide the service");
                }
                myAgent.send(reply);
            }

            MessageTemplate tp = MessageTemplate.and(MessageTemplate.MatchPerformative(ACLMessage.REJECT_PROPOSAL),
                    MessageTemplate.MatchConversationId("RetailerSelling"));
            ACLMessage ms = myAgent.receive(tp);
            if (ms != null)
                System.out
                        .println(myAgent.getLocalName() + "<=" + ms.getSender().getLocalName() + ":" + ms.getContent());
            demand -= 0.5;

        }

    }

    private class OfferingContract extends CyclicBehaviour {
        private Double kwhRequestbuy = 0.0;
        private long t;

        @Override
        public void action() {
            // Propose Selling price
            MessageTemplate mt = MessageTemplate.and(MessageTemplate.MatchPerformative(ACLMessage.CFP),
                    MessageTemplate.MatchConversationId("RetailerSelling"));
            ACLMessage msg = myAgent.receive(mt);
            t = System.currentTimeMillis();
            if (msg != null) {
                // Deplay offer
                Delay(timeResponse.getTime());
                // CFP Message received. Process it
                Double kwhRequestbuy = Double.parseDouble(msg.getContent());
                System.out.println(myAgent.getLocalName() + "<=" + msg.getSender().getLocalName() + ":"
                        + msg.getContent() + "[got usage]");
                ACLMessage reply = msg.createReply();
                // reply.setConversationId("RetailerSelling");
                // System.out.println(sellPrice.toString() + "|" + penaltyPrice.toString() + "|"
                // + timeContract.toString());
                String offer = pricing.getPrice() + "|" + penaltyPrice.toString() + "|" + timeContract.toString() + "|"
                        + kwhRequestbuy.toString();
                if (kwhRequestbuy != 0) {
                    // HA send retailer the usage !=0
                    reply.setPerformative(ACLMessage.PROPOSE);
                    reply.setConversationId("RetailerSelling");
                    reply.setContent(offer);
                    System.out.println(myAgent.getLocalName() + "=>" + msg.getSender().getLocalName() + ":" + offer
                            + "  [SellPrice|Penalty|Time|Kwh][REPLY TIME:" + (System.currentTimeMillis() - t)
                            + "](PROPOSE)");
                    myAgent.send(reply);
                } else {
                    // HA send retailer the usage =0
                    reply.setPerformative(ACLMessage.REFUSE);
                    // reply.setConversationId("RetailerSelling");
                    reply.setContent("Meaasage me expected kwh u demand!!");
                    System.out.println(myAgent.getLocalName() + "=>" + msg.getSender().getLocalName()
                            + ":Meaasage me expected kwh u demand!!(refuse)");
                    myAgent.send(reply);
                    demand -= 0.02;
                }
            }
        }
    }

    private class OfferingToBuyEnergy extends CyclicBehaviour {
        private Double kwhRequestsell = 0.0;
        private long t;

        @Override
        public void action() {
            MessageTemplate mt = MessageTemplate.and(MessageTemplate.MatchPerformative(ACLMessage.CFP),
                    MessageTemplate.MatchConversationId("RetailerBuying"));
            ACLMessage msg = myAgent.receive(mt);
            t = System.currentTimeMillis();
            if (msg != null) {
                // Delay send offer
                Delay(timeResponse.getTime());
                // CFP Message received. Process it
                Double kwhRequestsell = Double.parseDouble(msg.getContent());
                ACLMessage reply = msg.createReply();
                // System.out.println(sellPrice.toString() + "|" + penaltyPrice.toString() + "|"
                // + timeContract.toString());
                String offer = buyPrice.toString();
                ;
                if (kwhRequestsell != 0) {
                    // HA send retailer the usage !=0
                    reply.setPerformative(ACLMessage.PROPOSE);
                    reply.setConversationId("RetailerBuying");
                    reply.setContent(offer);
                    System.out.println(myAgent.getLocalName() + "=>" + msg.getSender().getLocalName() + ":$" + offer
                            + "kwh  [wanna buy] (Reply Time:" + (System.currentTimeMillis() - t) + ")");
                } else {
                    // HA send retailer the usage =0
                    reply.setPerformative(ACLMessage.REFUSE);
                    reply.setConversationId("RetailerSelling");
                    reply.setContent("Meaasage me expected kwh u wanna sell!!");
                    System.out.println(myAgent.getLocalName() + "=>" + msg.getSender().getLocalName()
                            + ":Meaasage me expected kwh u wanna sell!!");
                    demand -=0.2;

                }
                myAgent.send(reply);
            }

        }

    }

    public interface PricingStrategy {
        public double getPrice();
    }

    public class PricingStrategyFixed implements PricingStrategy {

        public double getPrice() {
            return sellPrice;
        }
    }

    public class YearlyPricingStrategy implements PricingStrategy {

        public double getPrice() {
            return sellPrice - 0.05;
        }
    }

    public class DemandPricingStrategy implements PricingStrategy {

        @Override
        public double getPrice() {
            double basePrice = sellPrice;
            double inflation = 0.02;
            // TODO Auto-generated method stub
            if(demand<-5) demand = -1;
            return basePrice + (inflation * demand);
        }

    }

    public interface TimeStrategy {
        public long getTime();
    }

    public class Quick implements TimeStrategy {
        public long getTime() {
            long n = ((int) (Math.random() * 1 + 1)) * 1000; // delay from 1-2s
            return n;
        }
    }

    public class Slow implements TimeStrategy {
        public long getTime() {
            long n = ((int) (Math.random() * 3 + 3)) * 1000; // delay from 3-6s
            return n;
        }
    }

}
