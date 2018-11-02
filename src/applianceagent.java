package fml;

import jade.core.AID;
import jade.core.Agent;
import jade.core.behaviours.CyclicBehaviour;
import jade.core.behaviours.OneShotBehaviour;
import jade.domain.DFService;
import jade.domain.FIPAAgentManagement.DFAgentDescription;
import jade.domain.FIPAAgentManagement.ServiceDescription;
import jade.domain.FIPAException;
import jade.lang.acl.ACLMessage;

/**
 * @author HeiTung @ Asus on 26/10/2018
 * @project EnergyTradingSystem
 * @package fm
 */
public class applianceagent extends Agent {
    // Internal state variables
    private double usage = 0;

    public applianceagent() {
    }

    @Override
    protected void setup() {
        System.out.println(getLocalName() + " await for message");
        // receive request message
        addBehaviour(new CyclicBehaviour(this) {
            @Override
            public void action() {
                ACLMessage msg = receive();
                if (msg != null) {
                    // handle message
                    if (msg.getPerformative() == ACLMessage.REQUEST) {
                        if (msg.getContent().contains("please provide your energy consumption")) {
                            usage = getEstimatedConsumption();
                        }
                        // reply to message
                        ACLMessage inform = msg.createReply();
                        inform.setPerformative(ACLMessage.INFORM);
                        inform.setContent(String.valueOf(getEstimatedConsumption()));
                        // send reply
                        send(inform);
                    }

                }
            }
        });
    }

    /**
     * get the estimation of current consumption
     * @return estimateConsumption
     */
    public double getEstimatedConsumption() {
        double estimateConsumption = Math.round(Math.random() * 100);
        if (estimateConsumption < 0) getEstimatedConsumption();
        return estimateConsumption;
    }

    /**
     * get the actual energy consumption
     * @return actualConsumption
     */
    public double getActualConsumption() {
        double actualConsumption = Math.round(Math.random() * 100);
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
            if (solar == true)
                sd.setType("Generating");
            else
                sd.setType("Consuming");
            sd.setName(getLocalName());
            dfd.addServices(sd);
            try {
                DFService.register(myAgent, dfd);
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
