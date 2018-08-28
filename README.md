# pegLog
Serverless location logging app that securely stores location data over a duration of multiple weeks

# whitelisting
With Android 6+ (Marshmallow), a new feature called doze mode was introduced, which can restrict activity on the device after certain periods of inactivity. 
You can choose to whitelist peglog which does not bypass doze mode but occasionally provides logging windows in which to work. 
to whitelist the appplicationm perform the following steps. This helps reduces the impact of battery optimisation techniques

1. Go to Settings → Battery

2. Tap on the top right 3-dot menu, and choose Battery Optimisation.

3. There should be a dropdown below the actionbar on the top left, choose All Apps from the list.

4. Find peglog in the list

5. Tap on it. You will get a popup with 2 options, Optimise and Don't Optimise

6. Optimise should be selected by default.

7. Select "Don't Optimise", and press "Done" below.
