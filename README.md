# peg log
Serverless location logging app that securely stores data. Primarily developed to assist with psychological research and behavioural science more generally. The app relies on the <a href="https://developers.google.com/location-context/fused-location-provider/">FusedLocationProvider</a> Application Programming Interface (API) (shortened to read ‘application’ in the published paper). This manages underlying location technologies, such as GPS and Wi-Fi, which are built into the Android operating system. 
 
Full Reference: 

Geyer, K., Ellis, D. A. and Piwek, L. (2019). A simple location-tracking app for psychological research.<i> Behavior Research Methods, 51</i>(6), 2840–2846 <a href="https://link.springer.com/article/10.3758/s13428-018-1164-y">LINK</a>

Download a working version from the <a href="https://play.google.com/store/apps/details?id=peglog.android.location.geyer.peglog1">Google Play Store</a>. This will provide a location update from the most accurate source (GPS, Wi-Fi, etc.) every 5 minutes.

A Terms of Service and Privacy Policy is available <a href="https://psychsensorlab.com/privacy-agreement-for-apps/">here</a>. 

Sample data and supplementary R-code for analysis are available <a href="https://drive.google.com/open?id=1HYb_GsvGLqP8RWOQRV7co_tEamYsHooA">here</a>. 

(password for encrypted data and error files=123456)

# customisation 
Which location data source (GPS, Wi-Fi, etc.) is used by default, and the frequency of
location updates can be customised by modifying the Constants file. Following customisation, the 
app can then be redistributed on the Google Play store.

# FAQs
1. Why are location readings not recorded at exactly x minutes as specified?

This is due to how Android OS currently implements its location system. While a location update can be requested every 5 minutes (for example), that's actually a suggestion rather than an imperative, and so the time interval between location points is never guaranteed. However, an exact timestamp of the recording is always reported alongside each reading. This allows researchers to monitor the exact duration between readings and/or compute related variables (e.g., speed) accurately.

2. A specified time interval has passed, but no co-ordinates were logged. Why?

There are several reasons this could happen.

(a) The GPS system may have attempted to report the current location and timed out. This is likely to be the result of a poor or absent signal. As a result, the operating system will have been unable to provide data to PEG LOG. An error log will be generated in this instance.

(b) On Android 6+ (Marshmallow), a new feature called ‘doze mode’ was introduced. This attempts to restrict activity on a device after certain periods of inactivity. Participants can choose to whitelist PEG LOG (see below), which can provide additional logging windows. 

# whitelisting
To whitelist the application perform the following steps. This helps reduces the impact of battery optimisation techniques.

1. Go to Settings → Battery

2. Tap on the top right 3-dot menu, and choose Battery Optimisation.

3. There should be a dropdown below the actionbar on the top left, choose All Apps from the list.

4. Find peglog in the list

5. Tap on it. You will get a popup with 2 options, Optimise and Don't Optimise

6. Optimise should be selected by default.

7. Select "Don't Optimise", and press "Done".

# bugs/known issues 
Report bugs or functionality issues to k.geyer2@lancaster.ac.uk
