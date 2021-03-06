package org.grails.plugin.googlemap.taglib

import grails.converters.JSON

/*
*
* @author Bhagwat Kumar
*/

class MapTagLib {
	static namespace = "googleMap"

	def config = grailsApplication.config
	def pluginManager

	def init = {attrs ->
		checkRequiredAttributes("init", attrs, ["sensor"])
		String sensor = attrs.remove('sensor')
		String version = attrs.remove('version')
		String language = attrs.remove('language')
		Boolean includeAddressAutoComplete = attrs.remove("addressAutoComplete") ?: false
		Map mapOptions = [sensor: sensor]
		if (language) {
			mapOptions += [language: language]
		}
		if (version) {
			mapOptions += [version: version]
		}

		def writer = out
//		writer << javascript(library: "jquery", plugin:'google-map')

		writer << '<script type="text/javascript" src="'
		writer << grailsApplication.config.map.api.url
		writer << '?'
		writer << mapOptions.collect {k, v -> k + "=" + v.encodeAsHTML()}.join("&")
		writer.println '"></script>'

		writer << javascript(library: "map.init", plugin: 'google-map')

		if (includeAddressAutoComplete) {
			writer << javascript(library: "geo_autocomplete", plugin: "google-map")
			writer << javascript(library: "jquery.autocomplete_geomod", plugin: "google-map")

			writer << '<link type="text/css" rel="stylesheet" href="'
			writer << resource(dir: pluginContextPath, file: 'css/jquery.autocomplete.css')
			writer.println '" />'
		}

		Map errorMessages=attrs.remove('errorMessages')
		if(errorMessages){
			writer.println '<script type="text/javascript">'
			writer.println 'var error_messages_json={};'
			errorMessages.collect{errorKey, message->
				writer.println "error_messages_json['${errorKey}']='${message.encodeAsJavaScript()}';"
			}
			writer.println 'googleMapManager.setErrorMessages(error_messages_json);'

			writer.println '</script>'
		}
	}

	def map = {attrs ->
		checkRequiredAttributes("map", attrs, ["name", "mapDivId"])

		String name = attrs.remove("name")
		String panorama = attrs.remove("panorama") ?: "${name}_panorama"
		String homeMarkerName = attrs.remove("homeMarkerName") ?: "${name}_homeMarker"
		String mapDivId = attrs.remove("mapDivId")
		String zoomString = attrs.remove("zoom")
		Integer zoom = zoomString ? zoomString.toInteger() : config.map.zoom
		String mapTypeId = attrs.remove("mapTypeId") ?: config.map.mapTypeId

		String latitudeId = attrs.remove("latitudeId")
		String longitudeId = attrs.remove("longitudeId")

		Map homeMarker = attrs.remove('homeMarker');
		String latitude = homeMarker["latitude"]
		String longitude = homeMarker["longitude"]

		String eventsScript = ""

		Map mapEventHandlers = attrs.remove("mapEventHandlers")
		Map homeMarkerEventHandlers = homeMarker.remove("eventHandler")
		Map streetViewEventHandlers = attrs.remove("streetViewEventHandlers")

		homeMarker = homeMarker.findAll {(it.key in ["zIndex", "draggable", "visible", "clickable", "flat", "raiseOnDrag", "title", "icon", "shadow", "cursor", "content"])}

		String homeMarkerJavaScript = "var ${homeMarkerName}=new google.maps.Marker(${homeMarker as JSON});"
		homeMarkerJavaScript += "${homeMarkerName}.setPosition(new google.maps.LatLng(${latitude}, ${longitude}));"

		["${name}": mapEventHandlers, "${homeMarkerName}": homeMarkerEventHandlers, "${panorama}": streetViewEventHandlers].each {String googleMapObject, Map eventHandler ->
			eventsScript += getEventHandlerJavaScript(googleMapObject, eventHandler)
		}

		List mapSettingsList = []//attrs.collect { k, v -> "$k:$v"}
		mapSettingsList.addAll(["mapTypeId:${mapTypeId}", "zoom:${zoom}"])
		String mapSettings = mapSettingsList.join(", ")

		out << """
		<script type="text/javascript">
				var ${name} , ${panorama};

				${homeMarkerJavaScript}
				jQuery(function () {
				${name}=googleMapManager.createMap('${mapDivId}',{${mapSettings}}, ${homeMarkerName},'${latitudeId}', '${longitudeId}')
				${panorama}=${name}.getStreetView();
				${eventsScript}
				});
		</script>
		 """
	}

	def searchAddressInput = {attrs ->
		String inputElementId = attrs.id ?: attrs.name
		String language = attrs.remove("language")
		String region = attrs.remove("region")
		Boolean selectFirst = attrs.remove('selectFirst') ?: false
		int minChars = attrs.remove('minChars')?.toInteger() ?: 3
		int cacheLength = attrs.remove('cacheLength')?.toInteger() ?: 50
		int width = attrs['width']?.toInteger() ?: 356
		boolean scroll = attrs.remove('scroll') ?: true
		int scrollHeight = attrs.remove('scrollHeight')?.toInteger() ?: 330

		String callBackFunctionPassed = attrs.remove('onComplete')
		String map = attrs.remove("map")
		String callBackFunction = "function(event, data){"
		if (map) {
			callBackFunction += "googleMapManager.updateHomeLocationMarker(${map}, jQuery('#${inputElementId}').val());"
		}

		if (callBackFunctionPassed) {
			callBackFunction += "${callBackFunctionPassed}(event,data);"
		}
		callBackFunction += "}"

		out << g.textField(attrs)

		Map searchAutoCompleteSettingsMap = [selectFirst: selectFirst, minChars: minChars, cacheLength: cacheLength, width: width, scroll: scroll, scrollHeight: scrollHeight]

		if (language) {searchAutoCompleteSettingsMap += ['lang': "'$language'"]}
		if (region) {searchAutoCompleteSettingsMap += ['region': "'$region'"]}

		String searchSettings = "{" + searchAutoCompleteSettingsMap.collect { k, v -> "$k:$v"}.join(",") + "}"
		out << """
		<script type="text/javascript">
				jQuery(function () {googleMapManager.initAutoComplete('#${inputElementId}',${searchSettings} ${callBackFunction ? ',' + callBackFunction : ''});});
		</script>
		"""

	}

	def directionLink = {attrs, body ->
		checkRequiredAttributes("directionLink", attrs, ["map", 'destination'])
		String map = attrs.remove("map")
		String panel = attrs.remove("panel")
		String origin = attrs.remove("origin")		 // address or (lat, long) pair
		String destination = attrs.remove("destination") // address or (lat, long) pair
		String travelMode = attrs.remove("travelMode") ?: "${config.map.default.travelMode}"
		String unitSystem = attrs.remove("unitSystem") ?: "${config.map.default.unitSystem}"

		String onClickHandler = "googleMapManager.showDirectionHandler(${map},'${panel}','${origin}', '${destination}', ${travelMode}, ${unitSystem});"

		if (origin) {
			onClickHandler = "googleMapManager.showDirectionHandler(${map},'${panel}','${origin}', '${destination}', ${travelMode}, ${unitSystem});"
		} else {
			origin = "googleMapManager.getHomeMarker($map).getPosition()"
			onClickHandler = "googleMapManager.showDirectionHandler(${map},'${panel}',${origin}, '${destination}', ${travelMode}, ${unitSystem});"
		}
		out << "<a href=\"#\" onClick=\"${onClickHandler}\" >${body()}</a>"
	}

	def hideDirection = {attrs, body ->
		checkRequiredAttributes("hideDirection", attrs, ["map"])
		String map = attrs.remove("map")
		String panel = attrs.remove("panel")

		String onClickHandler = "googleMapManager.hideDirection(${map}, '${panel}');"

		out << "<a href=\"#\" onClick=\"${onClickHandler}\" >${body()}</a>"
	}

	def streetViewLink = {attrs, body ->
		checkRequiredAttributes("streetViewLink", attrs, ["map", "address"])
		String map = attrs.remove("map")
		String address = attrs.remove("address")		 // address or (lat, long) pair
		String successHandler = attrs.remove("successHandler")
		String successHandlerStatement = successHandler ? ",${successHandler}" : ''
		String errorHandler = attrs.remove('errorHandler');
		String errorHandlerStatement = errorHandler ? ",${errorHandler},this" : ''

		String pitch = attrs.remove('pitch')
		String heading = attrs.remove('heading')
		String zoom = attrs.remove('zoom')
		String panoramaId = attrs.remove("panoramaId")

		Map streetViewSettings = [:]
		if (pitch) {streetViewSettings['pitch'] = pitch}
		if (heading) {streetViewSettings['heading'] = heading}
		if (zoom) {streetViewSettings['zoom'] = zoom}
		if (panoramaId) {streetViewSettings['panoramaId'] = panoramaId}

		String streetViewSettingsMap = ",{" + streetViewSettings.collect { k, v -> "$k:$v"}.join(",") + "}"

		String onClickHandler = "googleMapManager.showStreetView('${address}', ${map}${streetViewSettingsMap}${successHandlerStatement}${errorHandlerStatement});"

		out << "<a href=\"#\" onClick=\"${onClickHandler}\" >${body()}</a>"
	}

	def hideStreetView = {attrs, body ->
		checkRequiredAttributes("hideStreetView", attrs, ["map"])
		String map = attrs.remove("map")
		String panel = attrs.remove("panel")

		String onClickHandler = "googleMapManager.hideStreetView(${map}, '${panel}');"

		out << "<a href=\"#\" onClick=\"${onClickHandler}\" >${body()}</a>"
	}

	def updateMarkersOnMapFunction = {attrs ->
		checkRequiredAttributes("updateMarkersOnMapFunction", attrs, ["map", "markers"])
		String map = attrs.remove("map")
		def markers = attrs.remove("markers")
		Boolean clearOld = attrs.remove("clearOld") ?: true

		String onClickHandler = "googleMapManager.updateMarkersOnMap(${map}, ${markers}, ${clearOld});"
		out << onClickHandler
	}

	def updateMarkersOnMapLink = {attrs, body ->
		out << "<a href=\"#\" onClick=\"${googleMap.updateMarkersOnMapFunction(attrs)}\" >${body()}</a>"
	}

	def directionSearchHandler = {attrs ->
		checkRequiredAttributes("directionSearchHandler", attrs, ["map", "originDomId", "destinationDomId"])
		String map = attrs.remove('map')
		String panel = attrs.remove('panel')
		String originDomId = attrs.remove("originDomId")
		String destinationDomId = attrs.remove("destinationDomId")
		String travelModeDomId = attrs.remove("travelModeDomId")
		String unitSystemDomId = attrs.remove("unitSystemDomId")
		Boolean avoidHighways = attrs.remove("avoidHighways") ?: false
		Boolean avoidTolls = attrs.remove("avoidTolls") ?: false

		out << "googleMapManager.directionSearchHandler(${map},'${panel}','${originDomId}','${destinationDomId}','${travelModeDomId}', '${unitSystemDomId}',${avoidHighways}, ${avoidTolls})"

	}

	private void checkRequiredAttributes(String tagName, def attrs, List requiredAttributesList) {
		List missingAttributes = requiredAttributesList - attrs.keySet()
		if (missingAttributes) {
			throwTagError("Tag ${tagName} is missing required attribute(s) : [${missingAttributes.join(',')}]")
		}
	}

	private String getEventHandlerJavaScript(String googleMapObject, Map eventHandlersMap) {
		String eventsScript = ""
		eventHandlersMap.each {event, handler ->
			eventsScript += "google.maps.event.addListener(${googleMapObject}, '${event}', ${handler});\n"
		}
		return eventsScript
	}
}
