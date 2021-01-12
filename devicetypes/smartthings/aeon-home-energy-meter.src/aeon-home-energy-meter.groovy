/**
 *  Aeon Home Energy Meter
 *
 *  Author: WarrenK-design
 *
 *  Date: 10/11/2020
 * 	Description:
 *		This device handler is a modification on the Aeon Home Energy meter device handler which is also used by the Qubino meter. The device handler has been updated to support 
 *		Energy, Power and Voltage readings through an external REST API. The configuration paramater 40 has been updated for the device to report on a 1% change in power and the 
 *		parmater 42 has been set to report meter updates every 60 seconds if 1 % power change does not occur. Note 60 seconds is the minumum value for parameter 42. 
 *	TODO:
 *		According to Qubino there is no hub inbuit at the moment to get the produced energy from the meter 
 *		The meter needs to be queried by RateType to get this value see page 351 https://www.silabs.com/documents/login/miscellaneous/SDS13781-Z-Wave-Application-Command-Class-Specification.pdf
 */
metadata {
	definition (name: "Aeon Home Energy Meter", namespace: "smartthings", author: "WarrenK-design", runLocally: true, minHubCoreVersion: '000.017.0012', executeCommandsLocally: false, ocfDeviceType: "x.com.st.d.energymeter") {
		capability "Energy Meter"
		capability "Power Meter"
		capability "Configuration"
		capability "Sensor"
		capability "Health Check"
		capability "Refresh"
        capability "Voltage Measurement"

		command "reset"

		fingerprint deviceId: "0x2101", inClusters: " 0x70,0x31,0x72,0x86,0x32,0x80,0x85,0x60", deviceJoinName: "Aeon Energy Monitor"
		fingerprint mfr: "0086", prod: "0102", model: "005F", deviceJoinName: "Aeon Energy Monitor" // US //Home Energy Meter (Gen5)
		fingerprint mfr: "0086", prod: "0002", model: "005F", deviceJoinName: "Aeon Energy Monitor" // EU //Home Energy Meter (Gen5)
		fingerprint mfr: "0159", prod: "0007", model: "0052", deviceJoinName: "Qubino Energy Monitor" //Qubino Smart Meter
	}

	// simulator metadata
	simulator {
		for (int i = 0; i <= 10000; i += 1000) {
			status "power  ${i} W": new physicalgraph.zwave.Zwave().meterV3.meterReport(
					scaledMeterValue: i, precision: 3, meterType: 4, scale: 2, size: 4).incomingMessage()
		}
		for (int i = 0; i <= 100; i += 10) {
			status "energy  ${i} kWh": new physicalgraph.zwave.Zwave().meterV3.meterReport(
					scaledMeterValue: i, precision: 3, meterType: 0, scale: 0, size: 4).incomingMessage()
		}
	}

	// tile definitions
	tiles(scale: 2) {
     	// Current value of power
        valueTile("power", "device.power", decoration: "flat", width: 2, height: 2) {
			state "default", label:'${currentValue} W'
		} 
        // Current value of energy 
        valueTile("energy", "device.energy", decoration: "flat", width: 2, height: 2) {
			state "default", label:'${currentValue} kWh'
		} 
          
          // Current value of voltage 
        valueTile("voltage", "device.voltage", decoration: "flat", width: 2, height: 2) {
			state "default", label:'${currentValue} V'
		}
        
        
        // Reset Button
		standardTile("reset", "device.energy", inactiveLabel: false, decoration: "flat",width: 2, height: 2) {
			state "default", label:'reset kWh', action:"reset"
		}
        // Refresh button
		standardTile("refresh", "device.power", inactiveLabel: false, decoration: "flat",width: 2, height: 2) {
			state "default", label:'', action:"refresh.refresh", icon:"st.secondary.refresh"
		}
        // Configuration Button
		standardTile("configure", "device.power", inactiveLabel: false, decoration: "flat",width: 2, height: 2) {
			state "configure", label:'', action:"configuration.configure", icon:"st.secondary.configure"
		}
        
         // Show the tiles 
		details(["power","energy","voltage", "reset","refresh", "configure"])
	}
}



def installed() {
	log.debug "installed()..."
	sendEvent(name: "checkInterval", value: 1860, displayed: false, data: [protocol: "zwave", hubHardwareId: device.hub.hardwareID, offlinePingable: "0"])
	response(refresh())
}

def updated() {
	log.debug "updated()..."
	response(refresh())
}

def ping() {
	log.debug "STATUS ${device.status}"
	log.debug "ping()..."
	refresh()
}

// parse //
// This function is called anytime Z-Wave mesages are recieved from the meter 
def parse(String description) {
	//log.debug(description)
	def result = null
    // Parse and look for:
    //	31 - COMMAND_CLASS_SENSOR_MULTILEVEL V1
    //	32 - COMMAND_CLASS_METER V3
    //	60 - COMMAND_CLASS_MULTI_CHANNEL V3
	def cmd = zwave.parse(description, [0x31: 1, 0x32: 3, 0x60: 3])
    //log.debug(cmd)
    if (cmd) {
		result = createEvent(zwaveEvent(cmd))
	}
	log.debug "Parse returned ${result?.descriptionText}"
	return result
}

def zwaveEvent(physicalgraph.zwave.commands.securityv1.SecurityMessageEncapsulation cmd) {
	def encapsulatedCommand = cmd.encapsulatedCommand(versions)
	if (encapsulatedCommand) {
		zwaveEvent(encapsulatedCommand)
	} else {
		log.warn "Unable to extract encapsulated cmd from $cmd"
		createEvent(descriptionText: cmd.toString())
	}
}

def zwaveEvent(physicalgraph.zwave.commands.crc16encapv1.Crc16Encap cmd) {
	def version = versions[cmd.commandClass as Integer]
	def ccObj = version ? zwave.commandClass(cmd.commandClass, version) : zwave.commandClass(cmd.commandClass)
	def encapsulatedCommand = ccObj?.command(cmd.command)?.parse(cmd.data)
	if (encapsulatedCommand) {
		zwaveEvent(encapsulatedCommand)
	} else {
		[:]
	}
}

// This will be called when a meter report comes in from the meter 
def zwaveEvent(physicalgraph.zwave.commands.meterv3.MeterReport cmd) {
	// Call meter report function, pass cmd and cmd, keep seperate for now may have to do mre logic here 
    meterReport(cmd)
}

// meterReport //
// 	This function will pass the report sent from the meter located in the cmd variable
//	Based upon the report the function will update a given value, note only Energy, Voltage and Power supporeted through the
//	REST API at the moment using the Power Meter, Energy Meter and Voltage Measurement capability 
private meterReport(cmd) {
	def meterTypes = ["Unknown", "Electric", "Gas", "Water"]
    def electricNames = ["energy", "totalEnergy", "power", "count",  "voltage", "current", "powerFactor",  "unknown"]
    def electricUnits = ["kWh",    "kVAh",   "W",     "pulses", "V",       "A",       "Power Factor", ""]
    def map = [ name: electricNames[cmd.scale], unit: electricUnits[cmd.scale], displayed: state.display]
   
   	switch(cmd.rateType){
    	case 0:
        	break
        case 1:
            break
        case 2:
        	log.debug("2 Produced ${cmd}")
            break
    }
	//log.debug("Scale: ${cmd.scale} Type: ${electricNames[cmd.scale]} Value: ${cmd.scaledMeterValue} Units: ${electricUnits[cmd.scale]}")
    // Each reading has a scale attached to it, see page 356 https://www.silabs.com/documents/login/miscellaneous/SDS13781-Z-Wave-Application-Command-Class-Specification.pdf
	switch(cmd.scale) {
        case 0: //kWh
        	map.value = cmd.scaledMeterValue
            log.debug(cmd)
            log.debug("Scale: ${cmd.scale} Type: ${electricNames[cmd.scale]} Value: ${cmd.scaledMeterValue} Units: ${electricUnits[cmd.scale]} RateType: ${cmd.rateType}")
	        break
        case 1: //kVAh
            map.value = cmd.scaledMeterValue
            break;
        case 2: //Watts
            map.value = cmd.scaledMeterValue
            break;
        case 3: //pulses
            map.value = cmd.scaledMeterValue
            break;
        case 4: //Volts]
            map.value = cmd.scaledMeterValue
            break;
        case 5: //Amps
            map.value = cmd.scaledMeterValue
            break;
        case 6: //Power Factor
        	 map.value = cmd.scaledMeterValue
             break
        case 7: //Unknown
            map.value = cmd.scaledMeterValue
            break;
        default:
            break;
    }
    createEvent(map)
}

def zwaveEvent(physicalgraph.zwave.Command cmd) {
	// Handles all Z-Wave commands we aren't interested in
	[:]
}

def refresh() {
	log.debug "refresh()..."
	delayBetween([
			encap(zwave.associationV2.associationRemove(groupingIdentifier: 1, nodeId:[])), // Refresh Node ID in Group 1
   			encap(zwave.associationV2.associationSet(groupingIdentifier: 1, nodeId:zwaveHubNodeId)), //Assign Node ID of SmartThings to Group 1
			encap(zwave.meterV3.meterGet(scale: 0)),
            encap(zwave.meterV3.meterGet(scale: 1)),
            encap(zwave.meterV3.meterGet(scale: 2)),
            encap(zwave.meterV3.meterGet(scale: 3)),
			encap(zwave.meterV3.meterGet(scale: 4)),
            encap(zwave.meterV3.meterGet(scale: 5)),
            encap(zwave.meterV3.meterGet(scale: 6))      
	])
}

def reset() {
	log.debug "reset()..."
	// No V1 available
	delayBetween([
			encap(zwave.meterV3.meterReset()),
			encap(zwave.meterV3.meterGet(scale: 0))
	])
}

def configure() {
	log.debug "configure()..."
    // Aeotec Meter
	if (isAeotecHomeEnergyMeter())
		delayBetween([
				encap(zwave.configurationV1.configurationSet(parameterNumber: 101, size: 4, scaledConfigurationValue: 3)), // report total power in Watts and total energy in kWh...
				encap(zwave.configurationV1.configurationSet(parameterNumber: 102, size: 4, scaledConfigurationValue: 0)), // disable group 2...
				encap(zwave.configurationV1.configurationSet(parameterNumber: 103, size: 4, scaledConfigurationValue: 0)), // disable group 3...
				encap(zwave.configurationV1.configurationSet(parameterNumber: 111, size: 4, scaledConfigurationValue: 300)), // ...every 5 min
				encap(zwave.configurationV1.configurationSet(parameterNumber: 90, size: 1, scaledConfigurationValue: 0)), // enabling automatic reports, disabled selective reporting...
				encap(zwave.configurationV1.configurationSet(parameterNumber: 13, size: 1, scaledConfigurationValue: 0)) //disable CRC16 encapsulation
		], 500)
    // Qubino Meter
	else if (isQubinoSmartMeter())
		delayBetween([
				encap(zwave.configurationV1.configurationSet(parameterNumber: 40, size: 1, scaledConfigurationValue: 1)), // Device will report on 1% power change
				encap(zwave.configurationV1.configurationSet(parameterNumber: 42, size: 2, scaledConfigurationValue: 60)), // report every 60 seconds
		], 500)
	else
		delayBetween([
				encap(zwave.configurationV1.configurationSet(parameterNumber: 101, size: 4, scaledConfigurationValue: 4)),   // combined power in watts
				encap(zwave.configurationV1.configurationSet(parameterNumber: 111, size: 4, scaledConfigurationValue: 300)), // every 5 min
				encap(zwave.configurationV1.configurationSet(parameterNumber: 102, size: 4, scaledConfigurationValue: 8)),   // combined energy in kWh
				encap(zwave.configurationV1.configurationSet(parameterNumber: 112, size: 4, scaledConfigurationValue: 300)), // every 5 min
				encap(zwave.configurationV1.configurationSet(parameterNumber: 103, size: 4, scaledConfigurationValue: 0)),    // no third report
				encap(zwave.configurationV1.configurationSet(parameterNumber: 113, size: 4, scaledConfigurationValue: 300)) // every 5 min
		])
}

private encap(physicalgraph.zwave.Command cmd) {
	if (zwaveInfo.zw.contains("s")) {
		secEncap(cmd)
	} else if (zwaveInfo?.cc?.contains("56")){
		crcEncap(cmd)
	} else {
		cmd.format()
	}
}

private secEncap(physicalgraph.zwave.Command cmd) {
	zwave.securityV1.securityMessageEncapsulation().encapsulate(cmd).format()
}

private crcEncap(physicalgraph.zwave.Command cmd) {
	zwave.crc16EncapV1.crc16Encap().encapsulate(cmd).format()
}

private getVersions() {
	[
			0x32: 1,  // Meter
			0x70: 1,  // Configuration
			0x72: 1,  // ManufacturerSpecific
	]
}

private isAeotecHomeEnergyMeter() {
	zwaveInfo.model.equals("005F")
}

private isQubinoSmartMeter() {
	zwaveInfo.model.equals("0052")
}
