package testingpackage;

/**
 * @author ace wong on 12/10/2018
 * @project EnergyTradingSystem
 * @package testingpackage
 */
import jade.core.Agent;
import jade.domain.DFService;
import jade.domain.FIPAException;
import jade.domain.FIPAAgentManagement.DFAgentDescription;
import jade.domain.FIPAAgentManagement.ServiceDescription;

public class DFRegister extends Agent {

    /*
	// Method to register the service
	public static void register (Agent agent, String serviceType) {
		// Description of service to be registered

		DFAgentDescription dfd = new DFAgentDescription();
        ServiceDescription sd = new ServiceDescription();

        dfd.setName(agent.getAID());
		dfd.addServices(sd); 		// can register one or more services

		//sd.setType(serviceType);
		sd.setName(agent.getLocalName());


		// Register the agent and its services
		try {
			DFAgentDescription[] dfds = DFService.search(agent, dfd);
			if (dfds.length > 0 ) {
				DFService.deregister(agent, dfd);
			} else {
				DFService.register(agent, dfd);
				System.out.println(agent.getLocalName() + " is registered");
			}

		} catch (Exception e) {
			e.printStackTrace();
//			agent.doDelete();
		}
	}
	*/

    public void register(ServiceDescription sd){
        // Create new DF agent description
        DFAgentDescription dfd = new DFAgentDescription();
        dfd.setName(getAID());
        dfd.addServices(sd);
        try {
            //
        }
    }
}
