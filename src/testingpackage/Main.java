package testingpackage;

/**
 * @author ace wong on 7/10/2018
 * @project EnergyTradingSystem
 */
public class Main {

    public static void main (String[] args) {
        String[] args1 = {"-gui", "-agents", "Agent2:testingpackage.Agent2"};
        String[] args2 = {"-container", "-agents", "Agent1:testingpackage.Agent1"};
        jade.Boot.main(args1);
        jade.Boot.main(args2);
    }
}
