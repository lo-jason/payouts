package personal;

import com.paypal.api.payments.*;
import com.paypal.api.payments.Currency;
import com.paypal.base.rest.APIContext;
import com.paypal.base.rest.PayPalRESTException;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.math.BigDecimal;
import java.text.NumberFormat;
import java.text.ParseException;
import java.util.*;

public class PayoutMachine {

    public static final String USD = "USD";
    public static final String PAYOUT = "payout";
    public static final String E_MAIL = "e-mail";
    public static final String PO_NUMBER = "PO-number";

    public static void main(String[] args) throws Exception {
        if (args == null || args.length < 1 || args[0].equalsIgnoreCase("help")) {
            printUsage("");
        }

        // read CSV
        CSVParser records = CSVFormat.EXCEL.withHeader().parse(new FileReader(args[0]));

        // read secrets
        File file = new File("credentials");
        if (args.length >= 2) {
            File tempFile = new File(args[1]);
            if (tempFile.exists()) {
                file = tempFile;
            }
        }

        if (!file.exists()) {
            printUsage("Couldn't find credentials file, make sure it is specified or named 'credentials'");
        }

        BufferedReader bufferedReader = new BufferedReader(new FileReader(file));
        String clientId = bufferedReader.readLine();
        String secret = bufferedReader.readLine();

        if (isStringEmpty(clientId) || isStringEmpty(secret)) {
            printUsage(String.format("Could not detect clientId or secret, ClientId: %s Secret: %s", clientId, secret));
        }

        // read mode
        String mode = isSandbox(args) ? "sandbox" : "live";

        //run
        PayoutMachine payoutMachine = new PayoutMachine(records.getRecords(), clientId, secret);
        payoutMachine.payout(mode);
    }

    private static boolean isSandbox(String[] args) {
        for (String arg : args) {
            if (arg != null && arg.toLowerCase().equals("--sandbox")) {
                return true;
            }
        }
        return false;
    }

    private static boolean isStringEmpty(String s) {
        return s == null || s.length() == 0;
    }

    private static void printUsage(String s) {
        if (!isStringEmpty(s)) {
            System.out.println(s + "\n");
        }
        System.out.println("Usage: java -jar payout.jar <csvfile> --<live|sandbox>");
        System.out.println(String.format("Expects columns: %s, %s, %s", E_MAIL, PAYOUT, PO_NUMBER));
        System.out.println("");
        System.out.println("REQUIRED Arguments");
        System.out.println("<csv file>          csv file");
        System.out.println("");
        System.out.println("OPTIONAL Arguments");
        System.out.println("help                this message");
        System.out.println("<credentials file>  credentials file DEFAULT: credentials [no extension] in running directory");
        System.out.println("--sandbox           sandbox to test payments DEFAULT: live");
        System.exit(0);
    }

    private final String clientId;
    private final String clientSecret;
    private final Random random;
    private final List<CSVRecord> records;

    public PayoutMachine(List<CSVRecord> records, String clientId, String clientSecret) {
        random = new Random();
        this.records = records;
        this.clientId = clientId;
        this.clientSecret = clientSecret;
    }

    public PayoutBatch payout(String mode) throws ParseException {
        Payout payout = new Payout();
        PayoutSenderBatchHeader senderBatchHeader = new PayoutSenderBatchHeader();

        senderBatchHeader.setSenderBatchId(nextRandomLong())
                .setEmailSubject("Dcatast has sent you a payment");

        List<PayoutItem> items = new ArrayList<PayoutItem>();

        for (CSVRecord record : records) {
            Number parsedNumber = NumberFormat.getNumberInstance(Locale.US).parse(record.get(PAYOUT));
            float payoutAmount = round(parsedNumber.floatValue(), 2);
            while(payoutAmount > 0) {
                System.out.println(
                        String.format("Sending payment to %s of amount %f for PO: %s",
                                record.get(E_MAIL),
                                getLower(payoutAmount, 10000f),
                                record.get(PO_NUMBER)));

                // Amount
                Currency amount = new Currency();
                amount.setValue(Float.toString(getLower(payoutAmount, 10000f))).setCurrency(USD);

                PayoutItem item = new PayoutItem();
                item.setRecipientType("Email")
                        .setNote("For " + record.get(PO_NUMBER))
                        .setReceiver(record.get(E_MAIL))
                        .setSenderItemId(nextRandomLong()).setAmount(amount);
                items.add(item);

                // split payout if over 10k
                payoutAmount = payoutAmount - 10000;
            }
        }


        payout.setSenderBatchHeader(senderBatchHeader).setItems(items);

        // SEND REQUEST
        PayoutBatch batch = null;

        try {
            // TODO Get clientID and secret
            APIContext apiContext = new APIContext(clientId, clientSecret, mode);

            // ###Create Batch Payout
            batch = payout.create(apiContext, new HashMap<String, String>());

            System.out.println("Payout Batch With ID: " + batch.getBatchHeader().getPayoutBatchId());
            System.out.println("Payout Batch Create\n"
                    + Payout.getLastRequest() + "\n"
                    + "Check the status of the payment at the below URL"
                    + Payout.getLastResponse());

        } catch (PayPalRESTException e) {
            System.out.println("Payout failed due to: " + e.getMessage() + "\n\nResponse\n" +
                    Payout.getLastRequest());
        }

        return batch;
    }

    private float getLower(float f, float max) {
        if (f > max) {
            return max;
        }
        return f;
    }


    /**
     * Generate a Long
     * @return
     */
    private String nextRandomLong() {
        return new Long(random.nextLong()).toString();
    }

    /**
     * Round float to decimal places
     * @param d
     * @param decimalPlace
     * @return
     */
    public static float round(float d, int decimalPlace) {
        return BigDecimal.valueOf(d).setScale(decimalPlace, BigDecimal.ROUND_HALF_UP).floatValue();
    }
}
