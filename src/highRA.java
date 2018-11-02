package fml;

/**
 * @author Asus on 1/11/2018
 * @project EenergyTradingSystem
 * @package fml
 */
public class highRA extends retailagent{
    @Override
    public double getOfferPrice(double usage) {
        double price = 0.0;

        for (int i = 1; i < usage; i++) {
            //price = price + 100 * Math.pow(i, -0.77);
            price += 100 * Math.pow(i, -0.77);
    }
        return price;
    }
}
