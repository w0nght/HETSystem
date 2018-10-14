package testingpackage;

/**
 * @author ace wong on 7/10/2018
 * @project EnergyTradingSystem
 */
import jade.core.AID;
import jade.core.Agent;
import jade.core.behaviours.TickerBehaviour;
import jade.domain.DFService;
import jade.domain.FIPAAgentManagement.DFAgentDescription;
import jade.domain.FIPAAgentManagement.ServiceDescription;
import jade.domain.FIPAException;
import jade.domain.FIPANames;
import jade.lang.acl.ACLMessage;
import jade.proto.AchieveREInitiator;
import jade.proto.ContractNetInitiator;

import java.util.ArrayList;
import java.util.Date;
import java.util.Enumeration;
import java.util.Vector;
import java.util.concurrent.ThreadLocalRandom;

public class HomeAgent extends Agent {
    private Vector<String> retailers;
	private int nResponders;

	public HomeAgent() {
        retailers = new Vector();
    }

	protected void setup() {
		// Register agent
		// create service description for Home
		ServiceDescription sd = new ServiceDescription();
		sd.setType("home");
		sd.setName(getLocalName());
		// home agent register
        register(sd);

		// Setup home agent state
        Object[] args = getArguments();
        if (args != null && args.length> 0 ) {
            nResponders = args.length;
            System.out.println(("Requesting testing action to " + nResponders + " responders."));
            // call create request message method
            requestEnergy(a, 10);

        }

        //
		// find retailers
        DFAgentDescription[] retailers = getService("retailer");
        // find appliances
        DFAgentDescription[] appliances = getService("appliance");

        // test - send a test message
        testMessage(retailers);
        testMessage(appliances);

        // process
		//process();

        // ask appliances of required amount of energy
        int energyAmount = askForRequiredEnergy(appliances);
        requestEnergy(retailers, energyAmount);
	}

	// method to register home agent to DF Service
	void register(ServiceDescription sd) {
        // create new DF Agent Description
        DFAgentDescription dfd = new DFAgentDescription();
        dfd.setName(getAID());
        dfd.addServices(sd);
        try {
            DFService.register(this, dfd);
            System.out.println(getLocalName() + ": is now registered.");
        } catch (FIPAException fe) {
            fe.printStackTrace();
        }
    }

    // get list of agents offering the specified service
    DFAgentDescription[] getService (String serviceType) {
        DFAgentDescription[] result = null;
        try {
            DFAgentDescription dfd = new DFAgentDescription();
            ServiceDescription sd = new ServiceDescription();
            sd.setName(serviceType);
            dfd.addServices(sd);

            result = DFService.search(this, dfd);
            if (result != null) {
                System.out.println(getLocalName() + ": Successful searched for " + result.length + serviceType + " agent");
                // will del if not necessary
                if (result.length > 0) {
                    for (int i = 0; i < result.length; ++i) {
                        System.out.println(result[i].getName());
                    }
                }
            }
            System.out.println(getLocalName() + ": Unable to search for " + serviceType + " agent");
        } catch (FIPAException fe) {
            fe.printStackTrace();
        }
        return result;
    }

    // method to run the test
    void testMessage(DFAgentDescription[] serviceType) {
	    ACLMessage testMsg = new ACLMessage(ACLMessage.INFORM);
	    testMsg.setContent("testing testing testing.....");

	    for (int i = 0; i < serviceType.length; ++i) {
            testMsg.addReceiver(serviceType[i].getName());
        }
        send(testMsg);
	    addBehaviour(new TickerBehaviour(this, 1000) {
            public void onStart() {
                int nResponders = 0;
            }

            protected void onTick() {
                ACLMessage testMsg = receive();
                if (testMsg != null) {
                    nResponders++;
                    System.out.println(getLocalName()
                            + ": received a test message---" + testMsg.getContent()
                            + " from " + testMsg.getSender().getName()
                    );
                }
                if (serviceType.length <= nResponders) {
                    System.out.println(getLocalName() + ": received test respond from all " + serviceType.length + serviceType + " agents");
                    stop();
                }

            }
        });
    }

	void process() {

	}
/*
	public void behaviourConfiguration() {
		// add cyclic behavior loop the whole routine
		TickerBehaviour ticker = new TickerBehaviour(this, 2000) {
			@Override
			protected void onTick() {
                requestEnergy(retailers, energyAmount);
			}
		};
		addBehaviour(ticker);
	}
*/
    /* #1 send REQUEST to retailers */

    /**
     * #1 send REQUEST to appliances
     * forecasting the demand
     * @param appliances
     * @return
     */
    int askForRequiredEnergy(DFAgentDescription[] appliances) {
	    // TODO
        // setting up a random amount of energy
        // until figure out how to implement the forecastdemand()
        return ThreadLocalRandom.current().nextInt(100, 1000 + 1);
    }

    /** #3 send REQUEST to retailers **/
	// method of sending request to retailers
	// TODO
    // private void requestEnergy(DFAgentDescription[] retailers, int energyAmount) {
            void requestEnergy(args, int energyAmount){

		/*
		Object[] args = getArguments();
		if (args != null && args.length > 0) {
			nResponders = args.length;
			System.out.println("Requesting action to " + nResponders + " retailer agents");
	*/

//			ACLMessage cfpMsg = new ACLMessage(ACLMessage.CFP);
//
//			for (int i = 0; i < args.length; ++i) {
//				// add receivers
//				cfpMsg.addReceiver(new AID((String) args[i], AID.ISLOCALNAME));
//			}
        // create a CFP REQUEST message
		ACLMessage cfpMsg = new ACLMessage(ACLMessage.CFP);
		// add cfp receiver
        for (int i = 0; i < args.length; i++) {
		//for (String retailer: retailers) {
			//cfpMsg.addReceiver(new AID(retailer, AID.ISLOCALNAME));
            cfpMsg.addReceiver(new AID((String) args[i], AID.ISLOCALNAME));

        }
		// set the interaction protocol
		// specify the reply deadline in 10 seconds
		// start sending message

//		cfpMsg.setProtocol(FIPANames.InteractionProtocol.FIPA_REQUEST);
		cfpMsg.setProtocol(FIPANames.InteractionProtocol.FIPA_CONTRACT_NET);
		cfpMsg.setReplyByDate(new Date(System.currentTimeMillis() + 10000));
		cfpMsg.setContent("What is your price for xxx amount of energy?");

		// Initialise the Contract Net  behaviour and add to home agent
//			addBehaviour(new AchieveREInitiator(this, cfpMsg) {
		addBehaviour(new ContractNetInitiator(this, cfpMsg) {
			// to handle an agree message from responder
			protected void handlePropose(ACLMessage propose, Vector acceptances) {
			    System.out.println(getLocalName() + ": " + propose.getSender().getName() + " is proposing");
			}

			protected void handleAgree(ACLMessage agree) {
				System.out.println(getLocalName() + ": " + agree.getSender().getName() + " has agreed to the request");
			}
			// to handle an inform message from responder
			protected void handleInform(ACLMessage inform) {
				System.out.println(getLocalName() + ": " + inform.getSender().getName() + " successfully performed the requested action");
				System.out.println(getLocalName() + ": " + inform.getSender().getName() + "'s offer is " + inform.getContent());
			}
				// to handle a refuse message from responder
			protected void handleRefuse(ACLMessage refuse) {
			System.out.println(getLocalName() + ": " + refuse.getSender().getName() + " has refused to perform the requested action");
				nResponders--;
			}

			// to handle a failure message
			protected void handleFailure(ACLMessage failure) {
				if (failure.getSender().equals(myAgent.getAMS())) {
					// FAILURE notification from the JADE runtime: the receiver does not exist
					System.out.println(getLocalName() + ": Responder does not exist");
				} else {
					System.out.println(getLocalName() + ": " + failure.getSender().getName() + " failed to perform the requested action");
				}
			}
            protected void handleAllResponses(Vector responses, Vector acceptances) {
                //
                ACLMessage accept = null;
                Enumeration e = responses.elements();

                ArrayList<ACLMessage> proposals = new ArrayList<ACLMessage>();
                while (e.hasMoreElements()) {
                    ACLMessage msg = (ACLMessage) e.nextElement();
                    if (msg.getPerformative() == ACLMessage.PROPOSE) {
                        proposals.add(msg);
                    }
                }

                //
            }

            // to handle when notification have been received from all responders
			protected void handleAllResultNotifications(Vector notifications) {
				if (notifications.size() < nResponders) {
					// some responder did not reply within 10 seconds
					System.out.println(getLocalName() + ": Timeout expired: missing " + (nResponders - notifications.size()) + " responses");
				} else {
						System.out.println(getLocalName() + ": Received notifications about every responder");
					}
				}
			});
		}
}
