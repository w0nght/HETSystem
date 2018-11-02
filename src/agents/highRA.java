package agents;

/**
 * @author HeiTung @ Asus on 26/10/2018
 */
public class highRA extends RetailerAgent{
    @Override
    public float getOfferPrice(int usage) {
        int price = 0;

        for (int i = 1; i < usage; i++) {
            //price = price + 100 * Math.pow(i, -0.77);
            price += 100 * Math.pow(i, -0.5);
    }
        return price;
    }
}
