# CSV -> Paypal Payouts
  
```
Usage: java -jar payout.jar <csvfile> --<live|sandbox>
```
Expects columns: ```e-mail, payout, PO-number```
### REQUIRED Arguments
```
<csv file>          csv file
```

### OPTIONAL Arguments
```
help                this message
<credentials file>  credentials file DEFAULT: credentials [no extension] in running directory
--sandbox           sandbox to test payments DEFAULT: live
```
