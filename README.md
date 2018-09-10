# pegLog
Serverless location logging app that securely stores location data. Primarily developed to assist with psychological research and behavioural science more generally. 

# customisation 
Which location data source (GPS, Wi-Fi, etc.) is used by default, and the frequency of
location updates can be customised by modifying the Constants file. Following customisation, the 
application can then be redistributed on the Google Play store.

# whitelisting
To whitelist the appplication perform the following steps. This helps reduces the impact of battery optimisation techniques.

1. Go to Settings → Battery

2. Tap on the top right 3-dot menu, and choose Battery Optimisation.

3. There should be a dropdown below the actionbar on the top left, choose All Apps from the list.

4. Find peglog in the list

5. Tap on it. You will get a popup with 2 options, Optimise and Don't Optimise

6. Optimise should be selected by default.

7. Select "Don't Optimise", and press "Done".
