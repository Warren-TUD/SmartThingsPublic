# SmartThings Public GitHub Repo

This repo is just a fork of the [SmartThings Public Repo](https://github.com/SmartThingsCommunity/SmartThingsPublic) however it constains the updated device handler used in the ESHER project for the Qubino meter. 

The defualt device handler used by the Qubino meter for SmartThings did not support Voltage readings. The device only reported on a 10% change in power and the timing parmaeter for reporting was set to 5 minutes. 

The new device handler in this repo located [here](https://github.com/Warren-TUD/SmartThingsPublic/blob/master/devicetypes/smartthings/aeon-home-energy-meter.src/aeon-home-energy-meter.groovy) supports voltage readings and parses all readings sent by the meter displaying them in the logs. SmartThings only supports power, voltage and energy capabilities out of the box at the moment but work is being done on custom capbabilities so more values can be stored. At the moment the device handler supports Voltage (Volts), Power (Watts) and Energy (kWh). 

The Z-Wave configuration parameters for the meter have also been changed in the new device handler. Parameter 40 has been set to 1, meaning the Qubino meter will report readings based upon a 1% power (Watts) change. Parameter 42 has been set to 60 (lowest value) meaning the meter will report every 60 seconds if a value has changed, note this value seems to be reduntant as the 1% set in parameter 40 should capture all new meter values. 

The Qubino meter manual can be found [here](https://qubino.com/wp-content/uploads/2019/04/Qubino_Smart-Meter-PLUS-extended-manual_eng_3.4.pdf). 





Here are some links to help you get started coding right away:

* [GitHub-specific Documentation](http://docs.smartthings.com/en/latest/tools-and-ide/github-integration.html)
* [Full Documentation](http://docs.smartthings.com)
* [IDE & Simulator](http://ide.smartthings.com)
* [Community Forums](http://community.smartthings.com)

Follow us on the web:

* Twitter: http://twitter.com/smartthingsdev
* Facebook: http://facebook.com/smartthingsdevelopers
