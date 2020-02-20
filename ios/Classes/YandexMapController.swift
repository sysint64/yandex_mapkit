import CoreLocation
import Flutter
import UIKit
import YandexMapKit
import YandexMapKitTransport

func uiColorFromHex(value: Int) -> UIColor {
    let alpha = CGFloat((value >> 24) & 0xff) / 255.0
    let red = CGFloat((value >> 16) & 0xff) / 255.0
    let green = CGFloat((value >>  8) & 0xff) / 255.0
    let blue = CGFloat((value) & 0xff) / 255.0

    return UIColor(red: red, green: green, blue: blue, alpha: alpha)
}

func uiColorToHex(color: UIColor) -> Int {
    var colorRed: CGFloat = 0
    var colorGreen: CGFloat = 0
    var colorBlue: CGFloat = 0
    var colorAlpha: CGFloat = 0
    
    color.getRed(&colorRed, green: &colorGreen, blue: &colorBlue, alpha: &colorAlpha)
    
    let alpha = Int(floor(colorAlpha * 255.0)) & 0xff
    let red = Int(floor(colorRed * 255.0)) & 0xff
    let green = Int(floor(colorGreen * 255.0)) & 0xff
    let blue = Int(floor(colorBlue * 255.0)) & 0xff
    
    return alpha << 24 | red << 16 | green << 8 | blue
}

public class YandexMapController: NSObject, FlutterPlatformView {
  private let methodChannel: FlutterMethodChannel!
  private let pluginRegistrar: FlutterPluginRegistrar!
  private let mapObjectTapListener: MapObjectTapListener!
  private var userLocationObjectListener: UserLocationObjectListener?
  private var userLocationLayer: YMKUserLocationLayer?
  private var placemarks: [YMKPlacemarkMapObject] = []
  private var polylines: [YMKPolylineMapObject] = []
  private var routePolylines: [YMKPolylineMapObject] = []
  private var masstransitSectionInfoList: [SectionInfo] = []
  private var masstransitRoutePointsList: [RoutePoint] = []
  public let mapView: YMKMapView

  public var estimationRouteResult: FlutterResult?
  public var buildRouteResult: FlutterResult?
  public var searchResult: FlutterResult?

  private let masstransitRouter: YMKMasstransitRouter

  public struct PointBound {
      public let startPoint: RoutePoint
      public let endPoint: RoutePoint

      public init(
        startPoint: RoutePoint,
        endPoint: RoutePoint
      ) {
          self.startPoint = startPoint
          self.endPoint = endPoint
      }
  }

  public class RoutePoint {
      public let pointName: String
      public let color: Int
      public let zIndex: Int

      public init(
        pointName: String,
        color: Int,
        zIndex: Int
      ) {
        self.pointName = pointName
        self.color = color
        self.zIndex = zIndex
      }

      func serialize() -> [String: Any] {
        return [
          "name": self.pointName,
          "color": self.color,
          "zIndex": self.zIndex,
        ]
      }
  }

  public class SectionInfo {
      public let tag: String
      public let sectionDuration: Double
      public let walkingDistance: Double
      public let color: Int
      public let points: PointBound

      public init(
        tag: String,
        sectionDuration: Double,
        walkingDistance: Double,
        color: Int,
        points: PointBound
      ) {
          self.tag = tag
          self.sectionDuration = sectionDuration
          self.walkingDistance = walkingDistance
          self.color = color
          self.points = points
      }

      func serialize() -> [String: Any] {
          return [
            "tag": self.tag,
            "duration": self.sectionDuration,
            "walkingDistance": self.walkingDistance,
            "color": self.color,
            "points.startPoint": self.points.startPoint.serialize(),
            "points.endPoint": self.points.endPoint.serialize(),
          ]
      }
  }

  public class SectionTransport: SectionInfo {
      public let lineName: String
      public let lineId: String
      public let directionDesc: String
      public let transportInterval: String
      public let intermediateStations: [String]

      public init(
        tag: String,
        sectionDuration: Double,
        walkingDistance: Double,
        color: Int,
        points: PointBound,
        lineName: String,
        lineId: String,
        directionDesc: String,
        interval: String,
        intermediateStations: [String]
      ) {
          self.lineName = lineName
          self.lineId = lineId
          self.directionDesc = directionDesc
          self.transportInterval = interval
          self.intermediateStations = intermediateStations

          super.init(
            tag: tag,
            sectionDuration: sectionDuration,
            walkingDistance: walkingDistance,
            color: color,
            points: points
          )
      }

      override func serialize() -> [String: Any] {
          return [
            "tag": self.tag,
            "duration": self.sectionDuration,
            "walkingDistance": self.walkingDistance,
            "color": self.color,
            "points.startPoint": self.points.startPoint.serialize(),
            "points.endPoint": self.points.endPoint.serialize(),

            "lineName": self.lineName,
            "lineId": self.lineId,
            "directionDesc": self.directionDesc,
            "interval": self.transportInterval,
            "intermediateStations.size": self.intermediateStations.count,
          ]
      }
  }
    
  func maxPoint(p1: RoutePoint, p2: RoutePoint) -> RoutePoint {
    return p1.zIndex > p2.zIndex ? p1 : p2
  }
    
  func createRoutePoints(sections: [SectionInfo]) -> [RoutePoint] {
    var points: [RoutePoint] = []
    
    if sections.isEmpty {
        return points
    }
    
    // Add bound point
    points.append(sections[0].points.startPoint)
    var nextSection: SectionInfo
    
    for i in 0...sections.count - 2 {
        let section = sections[i]
        nextSection = sections[i + 1]
        
        let p1 = section.points.endPoint
        let p2 = nextSection.points.startPoint

        points.append(maxPoint(p1: p1, p2: p2))
    }
    
    //Add bound point
    points.append(sections[sections.count - 1].points.endPoint)
    return points
  }
    
  func mergeSectionInfoList(sections: [SectionInfo]) -> [SectionInfo] {
    var optimizedSections: [SectionInfo] = []
    var prevSection: SectionInfo? = nil
    
    for section in sections {
        if prevSection != nil {
            if prevSection!.tag == "pedestrian" && section.tag == "pedestrian" {
                prevSection = SectionInfo(
                    tag: prevSection!.tag,
                    sectionDuration: prevSection!.sectionDuration + section.sectionDuration,
                    walkingDistance: prevSection!.walkingDistance + section.walkingDistance,
                    color: prevSection!.color,
                    points: mergePoints(b1: prevSection!.points, b2: section.points)
                )
            } else {
                optimizedSections.append(prevSection!)
                prevSection = section
            }
        } else {
            prevSection = section
        }
    }
    
    if (prevSection != nil) {
        optimizedSections.append(prevSection!)
    }
    
    return optimizedSections
  }
    
  func mergePoints(b1: PointBound, b2: PointBound) -> PointBound {
    return PointBound(
        startPoint: maxPoint(p1: b1.startPoint, p2: b2.startPoint),
        endPoint: maxPoint(p1: b1.endPoint, p2: b2.endPoint)
    )
  }

  public required init(id: Int64, frame: CGRect, registrar: FlutterPluginRegistrar) {
    self.pluginRegistrar = registrar
    self.mapView = YMKMapView(frame: frame)
    self.methodChannel = FlutterMethodChannel(
      name: "yandex_mapkit/yandex_map_\(id)",
      binaryMessenger: registrar.messenger()
    )
    
    self.estimationRouteResult = nil
    self.buildRouteResult = nil
    self.searchResult = nil
    
    self.mapObjectTapListener = MapObjectTapListener(channel: methodChannel)
    self.userLocationLayer =
      YMKMapKit.sharedInstance().createUserLocationLayer(with: mapView.mapWindow)

    self.masstransitRouter = YMKTransport.sharedInstance().createMasstransitRouter();

    super.init()
    self.methodChannel.setMethodCallHandler(self.handle)
  }

  public func view() -> UIView {
    return self.mapView
  }

  public func handle(_ call: FlutterMethodCall, result: @escaping FlutterResult) {
    switch call.method {
    case "showUserLayer":
      showUserLayer(call)
      result(nil)
    case "hideUserLayer":
      hideUserLayer()
      result(nil)
    case "setMapStyle":
      setMapStyle(call)
      result(nil)
    case "move":
      move(call)
      result(nil)
    case "setBounds":
      setBounds(call)
      result(nil)
    case "addPlacemark":
      addPlacemark(call)
      result(nil)
    case "removePlacemark":
      removePlacemark(call)
      result(nil)
    case "addPolyline":
      addPolyline(call)
      result(nil)
    case "removePolyline":
      removePolyline(call)
      result(nil)
    case "zoomIn":
      zoomIn()
      result(nil)
    case "zoomOut":
      zoomOut()
      result(nil)
    case "getTargetPoint":
      let targetPoint = getTargetPoint()
      result(targetPoint)
    case "requestMasstransitRoute":
      self.buildRouteResult = result;
      requestMasstransitRoute(call);
    default:
      result(FlutterMethodNotImplemented)
    }
  }

  func requestRouterPoint(call: FlutterMethodCall) -> [YMKRequestPoint] {
    let params = call.arguments as! [String: Any]
    let startPoint = YMKPoint(
        latitude: params["srcLatitude"] as! Double,
        longitude: params["srcLongitude"] as! Double
    )
    let endPoint = YMKPoint(
        latitude: params["destLatitude"] as! Double,
        longitude: params["destLongitude"] as! Double
    )
    
    return [
        YMKRequestPoint(point: startPoint, type: .waypoint, pointContext: nil),
        YMKRequestPoint(point: endPoint, type: .waypoint, pointContext: nil),
    ]
  }

  public func requestMasstransitRoute(_ call: FlutterMethodCall) {
    let requestPoints = requestRouterPoint(call: call)

    let responseHandler = {(routesResponse: [YMKMasstransitRoute]?, error: Error?) -> Void in
        if let routes = routesResponse {
          self.onMasstransitRouteReceived(routes: routes)
        } else {
          self.onMasstransitRouteError(error: error!)
        }
    }

    if buildRouteResult != nil {
        clearRoute()
    }
    
    masstransitRouter.requestRoutes(
        with: requestPoints,
        masstransitOptions: YMKMasstransitOptions(),
        routeHandler: responseHandler
    )
  }
    
  func onMasstransitRouteReceived(routes: [YMKMasstransitRoute]) {
    masstransitSectionInfoList.removeAll()
    masstransitRoutePointsList.removeAll()
    
    if routes.count > 0 {
        if estimationRouteResult != nil {
            let estimation = routes[0].metadata.weight.time.text
            estimationRouteResult!(estimation)
        } else {
            for section in routes[0].sections {
                drawSection(
                    section: section,
                    geometry: routes[0].geometry
//                    geometry: YMKPolyline(points: section.geometry.points)
                )
            }
        }
        
        let mapObjects = mapView.mapWindow.map.mapObjects
        let polylineMapObject = mapObjects.addPolyline(with: routes[0].geometry)
        routePolylines.append(polylineMapObject)
    }
    
    masstransitSectionInfoList = mergeSectionInfoList(sections: masstransitSectionInfoList)
    masstransitRoutePointsList = createRoutePoints(sections: masstransitSectionInfoList)
    
    if buildRouteResult != nil {
        var result: [String: Any] = [:]
        var sections: [[String: Any]] = []
        var points: [[String: Any]] = []
        
        for section in masstransitSectionInfoList {
            sections.append(section.serialize())
        }
        
        for point in masstransitRoutePointsList {
            points.append(point.serialize())
        }
        
        result["sections"] = sections
        result["points"] = points
        
        buildRouteResult!(result)
    }
  }
    
  func onMasstransitRouteError(error: Error) {
    if buildRouteResult != nil {
        buildRouteResult!(
            FlutterError.init(
                code: "MasstransitRoutesError",
                message: "Unknown error",
                details: nil
            )
        )
    }
  }
    
  func drawSection(section: YMKMasstransitSection, geometry: YMKPolyline) {
//    let mapObjects = mapView.mapWindow.map.mapObjects
//    let polylineMapObject = mapObjects.addPolyline(with: geometry)
//    routePolylines.append(polylineMapObject)
    
    let info = getMasstransitSectionInfo(section: section)!
    
//    polylineMapObject.strokeColor = uiColorFromHex(value: info.color)
    masstransitSectionInfoList.append(info)
  }

  func clearRoute() {
    let mapObjects = mapView.mapWindow.map.mapObjects

    for polyline in routePolylines {
        mapObjects.remove(with: polyline)
    }
    
    routePolylines.removeAll()
  }
    
  func getMasstransitSectionInfo(section: YMKMasstransitSection) -> SectionInfo? {
    var color = 0xFFA06ED9
    var tag = ""
    var lineName = ""
    var directionDesc = ""
    var intermediateStations: [String] = []
    let data = section.metadata.data
    var zIndex = 0
    
    if data.transports != nil {
        for transport in data.transports! {
            let style = transport.line.style
            lineName = transport.line.name
            
            if style?.color != nil {
                color = style!.color!.intValue | 0xFF000000
                break
            }
        }
        
        let knownVehicleTypes: Set<String> = ["bus", "tramway", "underground"]
        
        for transport in data.transports! {
            let sectionVehicleType = getVehicleType(transport: transport, knownVehicleTypes: knownVehicleTypes)
            
            if sectionVehicleType != nil {
                break;
            } else if sectionVehicleType == "bus" {
                color = 0xFF33B609
                tag = "bus"
                zIndex = 1
            } else if sectionVehicleType == "tramway" {
                color = 0xFF33B609
                tag = "tramway"
                zIndex = 2
            } else if sectionVehicleType == "underground" {
                tag = "undeground"
                zIndex = 3
            }
        }
    } else {
        color = 0xFF7073EE
        tag = "pedestrian"
    }
    
    let duration = section.metadata.weight.time.value
    let walkingDistance = section.metadata.weight.walkingDistance.value
    var startPoint: RoutePoint
    var endPoint: RoutePoint
    
    if section.stops.count > 0 {
        startPoint = RoutePoint(
            pointName: section.stops.first!.stop.name,
            color: color,
            zIndex: zIndex
        )
        endPoint = RoutePoint(
            pointName: section.stops.last!.stop.name,
            color: color,
            zIndex: zIndex
        )
    } else {
        startPoint = RoutePoint(
            pointName: "",
            color: 0,
            zIndex: -1
        )
        endPoint = RoutePoint(
            pointName: "",
            color: 0,
            zIndex: -1
        )
    }
    
    let points = PointBound(startPoint: startPoint, endPoint: endPoint)
    
    if section.stops.count > 2 {
        let directionDesc = section.stops[1].stop.name
        
        for i in 1...section.stops.count - 2 {
            let stop = section.stops[i]
            intermediateStations.append(stop.stop.name)
        }
    }
    
    if tag == "pedestrian" {
        return SectionInfo(
            tag: tag,
            sectionDuration: duration,
            walkingDistance: walkingDistance,
            color: color,
            points: points
        )
    } else {
        return SectionTransport(
            tag: tag,
            sectionDuration: duration,
            walkingDistance: walkingDistance,
            color: color,
            points: points,
            lineName: lineName,
            lineId: "?",
            directionDesc: directionDesc,
            interval: "?",
            intermediateStations: intermediateStations
        )
    }
    
    return nil
  }
    
  func getVehicleType(transport: YMKMasstransitTransport, knownVehicleTypes: Set<String>) -> String? {
    for type in transport.line.vehicleTypes {
        if knownVehicleTypes.contains(type) {
            return type
        }
    }
    
    return nil
  }
    
  public func showUserLayer(_ call: FlutterMethodCall) {
    if (!hasLocationPermission()) { return }

    let params = call.arguments as! [String: Any]
    self.userLocationObjectListener = UserLocationObjectListener(
      pluginRegistrar: pluginRegistrar,
      iconName: params["iconName"] as! String
    )
    userLocationLayer?.setVisibleWithOn(true)
    userLocationLayer!.isHeadingEnabled = true
    userLocationLayer!.setObjectListenerWith(userLocationObjectListener!)
  }

  public func hideUserLayer() {
    if (!hasLocationPermission()) { return }

    userLocationLayer?.setVisibleWithOn(false)
  }

  public func setMapStyle(_ call: FlutterMethodCall) {
    let params = call.arguments as! [String: Any]
    let map = mapView.mapWindow.map
    map.setMapStyleWithStyle(params["style"] as! String)
  }

    public func zoomIn() {
        zoom(1)
    }

    public func zoomOut() {
        zoom(-1)
    }

    private func zoom(_ step: Float) {
        let point = mapView.mapWindow.map.cameraPosition.target
        let zoom = mapView.mapWindow.map.cameraPosition.zoom
        let azimuth = mapView.mapWindow.map.cameraPosition.azimuth
        let tilt = mapView.mapWindow.map.cameraPosition.tilt
        let currentPosition = YMKCameraPosition(
            target: point,
            zoom: zoom+step,
            azimuth: azimuth,
            tilt: tilt
         )
        mapView.mapWindow.map.move(
            with: currentPosition,
            animationType: YMKAnimation(
                type: YMKAnimationType.smooth,
                duration: 1
                ),
            cameraCallback: nil
        )
    }

  public func move(_ call: FlutterMethodCall) {
    let params = call.arguments as! [String: Any]
    let point = YMKPoint(latitude: params["latitude"] as! Double, longitude: params["longitude"] as! Double)
    let cameraPosition = YMKCameraPosition(
      target: point,
      zoom: params["zoom"] as! Float,
      azimuth: params["azimuth"] as! Float,
      tilt: params["tilt"] as! Float
    )

    moveWithParams(params, cameraPosition)
  }

  public func setBounds(_ call: FlutterMethodCall) {
    let params = call.arguments as! [String: Any]
    let cameraPosition = mapView.mapWindow.map.cameraPosition(with:
      YMKBoundingBox(
        southWest: YMKPoint(
          latitude: params["southWestLatitude"] as! Double,
          longitude: params["southWestLongitude"] as! Double
        ),
        northEast: YMKPoint(
          latitude: params["northEastLatitude"] as! Double,
          longitude: params["northEastLongitude"] as! Double
        )
      )
    )

    moveWithParams(params, cameraPosition)
  }

    public func getTargetPoint() -> [String: Any] {
    let targetPoint = mapView.mapWindow.map.cameraPosition.target;
        let arguments: [String: Any] = [
        "hashCode": targetPoint.hashValue,
        "latitude": targetPoint.latitude,
        "longitude": targetPoint.longitude
    ]
    return arguments
  }

  public func addPlacemark(_ call: FlutterMethodCall) {
    addPlacemarkToMap(call.arguments as! [String: Any])
  }


  public func removePlacemark(_ call: FlutterMethodCall) {
    let params = call.arguments as! [String: Any]
    let mapObjects = mapView.mapWindow.map.mapObjects
    let placemark = placemarks.first(where: { $0.userData as! Int == params["hashCode"] as! Int })

    if (placemark != nil) {
      mapObjects.remove(with: placemark!)
      placemarks.remove(at: placemarks.index(of: placemark!)!)
    }
  }

  private func addPlacemarkToMap(_ params: [String: Any]) {
    let point = YMKPoint(latitude: params["latitude"] as! Double, longitude: params["longitude"] as! Double)
    let mapObjects = mapView.mapWindow.map.mapObjects
    let placemark = mapObjects.addPlacemark(with: point)
    let iconName = params["iconName"] as? String

    placemark.addTapListener(with: mapObjectTapListener)
    placemark.userData = params["hashCode"] as! Int
    placemark.opacity = (Float)(params["opacity"] as! Double)
    placemark.isDraggable = params["isDraggable"] as! Bool

    if (iconName != nil) {
      placemark.setIconWith(UIImage(named: pluginRegistrar.lookupKey(forAsset: iconName!))!)
    }

    if let rawImageData = params["rawImageData"] as? FlutterStandardTypedData,
      let image = UIImage(data: rawImageData.data) {
        placemark.setIconWith(image)
    }
    placemarks.append(placemark)
  }

  private func addPolyline(_ call: FlutterMethodCall) {
    let params = call.arguments as! [String: Any]
    let coordinates = params["coordinates"] as! [[String: Any]]
    let coordinatesPrepared = coordinates.map { YMKPoint(latitude: $0["latitude"] as! Double, longitude: $0["longitude"] as! Double)}
    let mapObjects = mapView.mapWindow.map.mapObjects
    let polyline = YMKPolyline(points: coordinatesPrepared)
    let polylineMapObject = mapObjects.addPolyline(with: polyline)
    polylineMapObject.userData = params["hashCode"] as! Int
    polylineMapObject.strokeColor = uiColor(fromInt: params["strokeColor"] as! Int)
    polylineMapObject.outlineColor = uiColor(fromInt: params["outlineColor"] as! Int)
    polylineMapObject.outlineWidth = params["outlineWidth"] as! Float
    polylineMapObject.strokeWidth = params["strokeWidth"] as! Float
    polylineMapObject.isGeodesic = params["isGeodesic"] as! Bool
    polylineMapObject.dashLength = params["dashLength"] as! Float
    polylineMapObject.dashOffset = params["dashOffset"] as! Float
    polylineMapObject.gapLength = params["gapLength"] as! Float
    polylines.append(polylineMapObject)
  }

  private func removePolyline(_ call: FlutterMethodCall) {
    let params = call.arguments as! [String: Any]
    let hashCode = params["hashCode"] as! Int

    if let polyline = polylines.first(where: { $0.userData as! Int ==  hashCode}) {
      let mapObjects = mapView.mapWindow.map.mapObjects
      mapObjects.remove(with: polyline)
      polylines.remove(at: polylines.index(of: polyline)!)
    }
  }

  private func moveWithParams(_ params: [String: Any], _ cameraPosition: YMKCameraPosition) {
    if (params["animate"] as! Bool) {
      let type = params["smoothAnimation"] as! Bool ? YMKAnimationType.smooth : YMKAnimationType.linear
      let animationType = YMKAnimation(type: type, duration: params["animationDuration"] as! Float)

      mapView.mapWindow.map.move(with: cameraPosition, animationType: animationType)
    } else {
      mapView.mapWindow.map.move(with: cameraPosition)
    }
  }

  private func hasLocationPermission() -> Bool {
    if CLLocationManager.locationServicesEnabled() {
      switch CLLocationManager.authorizationStatus() {
      case .notDetermined, .restricted, .denied:
        return false
      case .authorizedAlways, .authorizedWhenInUse:
        return true
      }
    } else {
      return false
    }
  }

  private func uiColor(fromInt value: Int) -> UIColor {
    return UIColor(red: CGFloat((value & 0xFF0000) >> 16) / 0xFF,
                   green: CGFloat((value & 0x00FF00) >> 8) / 0xFF,
                   blue: CGFloat(value & 0x0000FF) / 0xFF,
                   alpha: CGFloat((value & 0xFF000000) >> 24) / 0xFF)
  }

  internal class UserLocationObjectListener: NSObject, YMKUserLocationObjectListener {
    private let pluginRegistrar: FlutterPluginRegistrar!
    private let iconName: String!

    public required init(pluginRegistrar: FlutterPluginRegistrar, iconName: String) {
      self.pluginRegistrar = pluginRegistrar
      self.iconName = iconName
    }

    func onObjectAdded(with view: YMKUserLocationView) {
      view.pin.setIconWith(
        UIImage(named: pluginRegistrar.lookupKey(forAsset: self.iconName))!
      )
    }

    func onObjectRemoved(with view: YMKUserLocationView) {}

    func onObjectUpdated(with view: YMKUserLocationView, event: YMKObjectEvent) {}
  }

  internal class MapObjectTapListener: NSObject, YMKMapObjectTapListener {
    private let methodChannel: FlutterMethodChannel!

    public required init(channel: FlutterMethodChannel) {
      self.methodChannel = channel
    }

    func onMapObjectTap(with mapObject: YMKMapObject, point: YMKPoint) -> Bool {
      let arguments: [String:Any?] = [
        "hashCode": mapObject.userData,
        "latitude": point.latitude,
        "longitude": point.longitude
      ]
      methodChannel.invokeMethod("onMapObjectTap", arguments: arguments)

      return true
    }
  }
}
