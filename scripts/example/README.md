# Example Polestar Configuration

This directory contains a set of scripts to configure a polestar instance with a basic example configuration. The .zip can be uploaded directly into polestar by clicking on the upload button on the polestar scripts page (only visible on large screen devices) The .xml files are here expanded so that you can browse and select them individually from github

## About this configuration

Here is a list of the example script files with a description of what they do. More details can be found in the [help page](https://polestar.io/polestar/help).

* *Configuration* - configures the header with title, icon and subtitle
* *SensorList* - defines a set of 5 sensors, 3 of which are set locally using the 'Update Sensors' script and one of which is set using a web hook with 'Demo Webhook'. The sensors are a random number generator, a 24h ramp generator, a daylight simulator for GMT timezone, the state of a light switch and the value gained from an external source via a webook.
* *Homepage* - a script that defines the content of the homepage, we place a single visualization on it with an iframe.
* *Graph_Daylight_and_light_switch* -  generates a 24h graph of simulated daylight and the switch status (which turns on when dark)
* *onError* - this script is triggered when an error occurs on any sensor. In this example it simply writes a warning to the log.
* *Update_Sensors* - updates random number generator, ramp and daylight every 5 minutes.
* *Demo_Webhook* - when called with a 'value' parameter it will update a sensor
* *Switch_on_lights_when_dark* - runs periodically and will switch light on and off based on the level of daylight
* *Javascript_Graph_Library* - script serves a static javascript library that is used by the visualization.
* *Test_Startup_Hook* - called when polestar is started.