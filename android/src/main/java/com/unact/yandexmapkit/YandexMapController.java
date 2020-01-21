package com.unact.yandexmapkit;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;

import androidx.annotation.NonNull;
import androidx.core.app.ActivityCompat;

import android.util.Log;
import android.view.View;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;

import com.yandex.mapkit.*;
import com.yandex.mapkit.directions.DirectionsFactory;
import com.yandex.mapkit.geometry.BoundingBox;
import com.yandex.mapkit.geometry.Geo;
import com.yandex.mapkit.geometry.Point;
import com.yandex.mapkit.geometry.Polyline;
import com.yandex.mapkit.geometry.SubpolylineHelper;
import com.yandex.mapkit.layers.GeoObjectTapEvent;
import com.yandex.mapkit.layers.GeoObjectTapListener;
import com.yandex.mapkit.layers.ObjectEvent;
import com.yandex.mapkit.map.*;
import com.yandex.mapkit.mapview.MapView;
import com.yandex.mapkit.search.*;
import com.yandex.mapkit.search.Session;
import com.yandex.mapkit.search.Session.SearchListener;
import com.yandex.mapkit.transport.TransportFactory;
import com.yandex.mapkit.transport.bicycle.BicycleRouter;
import com.yandex.mapkit.transport.masstransit.*;
import com.yandex.mapkit.transport.masstransit.Line;
import com.yandex.mapkit.transport.masstransit.Session.RouteListener;
import com.yandex.mapkit.directions.driving.DrivingRouter;
import com.yandex.mapkit.directions.driving.DrivingOptions;
import com.yandex.mapkit.directions.driving.DrivingSession;
import com.yandex.mapkit.directions.driving.DrivingRoute;
import com.yandex.mapkit.user_location.UserLocationLayer;
import com.yandex.mapkit.user_location.UserLocationObjectListener;
import com.yandex.mapkit.user_location.UserLocationView;
import com.yandex.runtime.Error;
import com.yandex.runtime.image.ImageProvider;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import io.flutter.plugin.common.MethodCall;
import io.flutter.plugin.common.MethodChannel;
import io.flutter.plugin.common.PluginRegistry;
import io.flutter.plugin.platform.PlatformView;

public class YandexMapController implements PlatformView, MethodChannel.MethodCallHandler,
  RouteListener, SearchListener, com.yandex.mapkit.transport.bicycle.Session.RouteListener,
  DrivingSession.DrivingRouteListener
{
  static class PointBound {
    final RoutePoint startPoint;
    final RoutePoint endPoint;

    PointBound(RoutePoint startPoint, RoutePoint endPoint) {
      this.startPoint = startPoint;
      this.endPoint = endPoint;
    }
  }

  static class RoutePoint {
    final String name;
    final int color;
    final int zIndex;

    RoutePoint(String name, int color, int zIndex) {
      this.name = name;
      this.color = color;
      this.zIndex = zIndex;
    }

    @NonNull
    @Override
    public String toString() {
      return "  RoutePoint(\n" +
        "    name: " + name + ",\n" +
        "    color: " + color + ",\n" +
        "    zIndex: " + zIndex + "\n" +
        "  )\n";
    }

    Map<String, Object> serialize() {
      final Map<String, Object> map = new HashMap<>();

      map.put("name", name);
      map.put("color", color);
      map.put("zIndex", zIndex);

      return map;
    }
  }

  static class SectionInfo {
    final String tag;
    final double duration;
    final double walkingDistance;
    final int color;
    final PointBound points;

    SectionInfo(
      String tag,
      double duration,
      double distance,
      int color,
      PointBound points
    ) {
      this.tag = tag;
      this.duration = duration;
      this.walkingDistance = distance;
      this.color = color;
      this.points = points;
    }

    @NonNull
    @Override
    public String toString() {
      return "SectionInfo(\n" +
        "  tag: " + tag + ",\n" +
        "  duration: " + duration + ",\n" +
        "  walkingDistance: " + walkingDistance + ",\n" +
        "  color: " + color + ",\n" +
        "  points.startPoint: " + points.startPoint.toString() +
        "  points.endPoint: " + points.endPoint.toString() +
        ")\n";
    }

    Map<String, Object> serialize() {
      final Map<String, Object> map = new HashMap<>();

      map.put("tag", tag);
      map.put("duration", duration);
      map.put("walkingDistance", walkingDistance);
      map.put("color", color);
      map.put("points.startPoint", points.startPoint.serialize());
      map.put("points.endPoint", points.endPoint.serialize());

      return map;
    }
  }

  static class SectionTransport extends SectionInfo {
    final String lineName;
    final String lineId;
    final String directionDesc;
    final String interval;
    final List<String> intermediateStations;

    SectionTransport(
      String tag,
      double duration,
      double walkingDistance,
      int color,
      String lineName,
      String lineId,
      String directionDesc,
      String interval,
      List<String> intermediateStations,
      PointBound points
    ) {
      super(tag, duration, walkingDistance, color, points);

      this.lineName = lineName;
      this.lineId = lineId;
      this.directionDesc = directionDesc;
      this.interval = interval;
      this.intermediateStations = intermediateStations;
    }

    @NonNull
    @Override
    public String toString() {
      return "SectionTransport(\n" +
        "  tag: " + tag + ",\n" +
        "  duration: " + duration + ",\n" +
        "  walkingDistance: " + walkingDistance + ",\n" +
        "  color: " + color + ",\n" +
        "  lineName: " + lineName + ",\n" +
        "  lineId: " + lineId + ",\n" +
        "  directionDesc: " + directionDesc + ",\n" +
        "  interval: " + interval + ",\n" +
        "  intermediateStations: " + intermediateStationsToString() + ",\n" +
        "  points.startPoint: " + points.startPoint.toString() +
        "  points.endPoint: " + points.endPoint.toString() +
        ")\n";
    }

    private String intermediateStationsToString() {
      final StringBuilder builder = new StringBuilder();
      builder.append("[");

      for (final String stop : intermediateStations) {
        builder.append(stop);
        //noinspection StringEquality
        if (stop != intermediateStations.get(intermediateStations.size() - 1)) {
          builder.append(", ");
        }
      }

      builder.append("]");
      return builder.toString();
    }

    @Override
    Map<String, Object> serialize() {
      final Map<String, Object> map = new HashMap<>();

      map.put("tag", tag);
      map.put("duration", duration);
      map.put("walkingDistance", walkingDistance);
      map.put("color", color);
      map.put("lineName", lineName);
      map.put("lineId", lineId);
      map.put("directionDesc", directionDesc);
      map.put("interval", interval);
      map.put("intermediateStations.size", intermediateStations.size());
      map.put("points.startPoint", points.startPoint.serialize());
      map.put("points.endPoint", points.endPoint.serialize());

      return map;
    }
  }

  private final MapView mapView;
  private final MethodChannel methodChannel;
  private final PluginRegistry.Registrar pluginRegistrar;
  private YandexUserLocationObjectListener yandexUserLocationObjectListener;
  private YandexMapObjectTapListener yandexMapObjectTapListener;
  private UserLocationLayer userLocationLayer;
  private List<PlacemarkMapObject> placemarks = new ArrayList<>();
  private List<PolylineMapObject> polylines = new ArrayList<>();
  private List<SectionInfo> masstransitSectionInfoList = new ArrayList<>();
  private List<RoutePoint> masstransitRoutePointsList = new ArrayList<>();
  private String userLocationArrowIconName;
  private String userLocationPinIconName;

  private MasstransitRouter masstransitRouter;
  private PedestrianRouter pedestrianRouter;
  private BicycleRouter bicycleRouter;
  private DrivingRouter drivingRouter;

  private final List<PolylineMapObject> routePolylines = new ArrayList<>();
  private GeoObjectTapListener geoObjectTapListener;

  private MethodChannel.Result estimationRouteChannel;
  private MethodChannel.Result buildRouteChannel;
  private MethodChannel.Result searchChannel;

  private SearchManager searchManager;
  private Session searchSession;

  public YandexMapController(int id, Context context, PluginRegistry.Registrar registrar) {
    MapKitFactory.initialize(context);
    TransportFactory.initialize(context);
    DirectionsFactory.initialize(context);

    mapView = new MapView(context);
    MapKitFactory.getInstance().onStart();
    mapView.onStart();
    pluginRegistrar = registrar;
    yandexMapObjectTapListener = new YandexMapObjectTapListener();
    userLocationLayer = MapKitFactory.getInstance().createUserLocationLayer(mapView.getMapWindow());
    yandexUserLocationObjectListener = new YandexUserLocationObjectListener(registrar);

    methodChannel = new MethodChannel(registrar.messenger(), "yandex_mapkit/yandex_map_" + id);
    methodChannel.setMethodCallHandler(this);

    masstransitRouter = TransportFactory.getInstance().createMasstransitRouter();
    pedestrianRouter = TransportFactory.getInstance().createPedestrianRouter();
    bicycleRouter = TransportFactory.getInstance().createBicycleRouter();
    drivingRouter = DirectionsFactory.getInstance().createDrivingRouter();

    // Search
    SearchFactory.initialize(context);
    searchManager = SearchFactory.getInstance().createSearchManager(SearchManagerType.COMBINED);
  }

  @Override
  public View getView() {
    if (geoObjectTapListener != null) {
      mapView.getMap().removeTapListener(geoObjectTapListener);
    }

    geoObjectTapListener = new GeoObjectTapListener() {
      @Override
      public boolean onObjectTap(@NonNull GeoObjectTapEvent geoObjectTapEvent) {
        Log.d("DONE", "START");
        Map<String, Object> arguments = new HashMap<>();

        final GeoObject geoObject = geoObjectTapEvent.getGeoObject();

        arguments.put("name", geoObject.getName());
        arguments.put("description", geoObject.getDescriptionText());

        if (!geoObject.getGeometry().isEmpty()) {
          final Point point = geoObject.getGeometry().get(0).getPoint();

          if (point != null) {
            arguments.put("latitude", point.getLatitude());
            arguments.put("longitude", point.getLongitude());
          } else {
            arguments.put("latitude", 0.0);
            arguments.put("longitude", 0.0);
          }
        } else {
          arguments.put("latitude", 0.0);
          arguments.put("longitude", 0.0);
        }

        Log.d("DONE", "DONE");
        methodChannel.invokeMethod("onGeoObjectTap", arguments);
        Log.d("DONE", "INVOKED");
        return true;
      }
    };

    mapView.getMap().addTapListener(geoObjectTapListener);
    return mapView;
  }

  @Override
  public void dispose() {
    mapView.onStop();
    MapKitFactory.getInstance().onStop();
  }

  @SuppressWarnings("unchecked")
  private void showUserLayer(MethodCall call) {
    if (!hasLocationPermission()) return;

    Map<String, Object> params = ((Map<String, Object>) call.arguments);
    userLocationArrowIconName = (String) params.get("arrowIconName");
    userLocationPinIconName = (String) params.get("pinIconName");

    userLocationLayer.setVisible(true);
    userLocationLayer.setHeadingEnabled(false);
    userLocationLayer.setObjectListener(yandexUserLocationObjectListener);
  }

  private void hideUserLayer() {
    if (!hasLocationPermission()) return;

    userLocationLayer.setVisible(false);
  }

  @SuppressWarnings("unchecked")
  private void setMapStyle(MethodCall call) {
    Map<String, Object> params = ((Map<String, Object>) call.arguments);
    mapView.getMap().setMapStyle((String) params.get("style"));
  }

  @SuppressWarnings("unchecked")
  private void move(MethodCall call) {
    Map<String, Object> params = ((Map<String, Object>) call.arguments);
    Point point = new Point(((Double) params.get("latitude")), ((Double) params.get("longitude")));
    CameraPosition cameraPosition = new CameraPosition(
      point,
      ((Double) params.get("zoom")).floatValue(),
      ((Double) params.get("azimuth")).floatValue(),
      ((Double) params.get("tilt")).floatValue()
    );

    moveWithParams(params, cameraPosition);
  }

  @SuppressWarnings("unchecked")
  private void setBounds(MethodCall call) {
    Map<String, Object> params = ((Map<String, Object>) call.arguments);
    BoundingBox boundingBox = new BoundingBox(
      new Point(((Double) params.get("southWestLatitude")), ((Double) params.get("southWestLongitude"))),
      new Point(((Double) params.get("northEastLatitude")), ((Double) params.get("northEastLongitude")))
    );

    moveWithParams(params, mapView.getMap().cameraPosition(boundingBox));
  }

  @SuppressWarnings("unchecked")
  private void addPlacemark(MethodCall call) {
    addPlacemarkToMap(((Map<String, Object>) call.arguments));
  }

  private Map<String, Object> getTargetPoint() {
    Point point = mapView.getMapWindow().getMap().getCameraPosition().getTarget();
    Map<String, Object> arguments = new HashMap<>();
    arguments.put("hashCode", point.hashCode());
    arguments.put("latitude", point.getLatitude());
    arguments.put("longitude", point.getLongitude());
    return arguments;
  }

  @SuppressWarnings("unchecked")
  private void removePlacemark(MethodCall call) {
    Map<String, Object> params = ((Map<String, Object>) call.arguments);
    MapObjectCollection mapObjects = mapView.getMap().getMapObjects();
    Iterator<PlacemarkMapObject> iterator = placemarks.iterator();

    while (iterator.hasNext()) {
      PlacemarkMapObject placemarkMapObject = iterator.next();
      if (placemarkMapObject.getUserData().equals(params.get("hashCode"))) {
        mapObjects.remove(placemarkMapObject);
        iterator.remove();
      }
    }
  }

  @SuppressWarnings("unchecked")
  private void updatePlacemarkPoint(MethodCall call) {
    Map<String, Object> params = ((Map<String, Object>) call.arguments);
    Point point = new Point(((Double) params.get("latitude")), ((Double) params.get("longitude")));

    MapObjectCollection mapObjects = mapView.getMap().getMapObjects();
    Iterator<PlacemarkMapObject> iterator = placemarks.iterator();

    while (iterator.hasNext()) {
      PlacemarkMapObject placemarkMapObject = iterator.next();
      if (placemarkMapObject.getUserData().equals(params.get("hashCode"))) {
        placemarkMapObject.setGeometry(point);
      }
    }
  }

  private void addPlacemarkToMap(Map<String, Object> params) {
    Point point = new Point(((Double) params.get("latitude")), ((Double) params.get("longitude")));
    MapObjectCollection mapObjects = mapView.getMap().getMapObjects();
    PlacemarkMapObject placemark = mapObjects.addPlacemark(point);
    String iconName = (String) params.get("iconName");
    byte[] rawImageData = (byte[]) params.get("rawImageData");

    placemark.setUserData(params.get("hashCode"));
    placemark.setOpacity(((Double) params.get("opacity")).floatValue());
    placemark.setDraggable((Boolean) params.get("isDraggable"));
    placemark.addTapListener(yandexMapObjectTapListener);

    if (iconName != null) {
      placemark.setIcon(ImageProvider.fromAsset(mapView.getContext(), pluginRegistrar.lookupKeyForAsset(iconName)));
    }

    if (rawImageData != null) {
      Bitmap bitmapData = BitmapFactory.decodeByteArray(rawImageData, 0, rawImageData.length);
      placemark.setIcon(ImageProvider.fromBitmap(bitmapData));
    }

    placemarks.add(placemark);
  }

  private void addPolyline(MethodCall cell) {
    Map<String, Object> params = (Map<String, Object>) cell.arguments;
    List<Map<String, Object>> coordinates = (List<Map<String, Object>>) params.get("coordinates");
    ArrayList<Point> polylineCoordinates = new ArrayList<>();
    for (Map<String, Object> c : coordinates) {
      Point p = new Point((Double) c.get("latitude"), (Double) c.get("longitude"));
      polylineCoordinates.add(p);
    }
    MapObjectCollection mapObjects = mapView.getMap().getMapObjects();
    PolylineMapObject polyline = mapObjects.addPolyline(new Polyline(polylineCoordinates));

    String outlineColorString = String.valueOf(params.get("outlineColor"));
    Long outlineColorLong = Long.parseLong(outlineColorString);

    String strokeColorString = String.valueOf(params.get("strokeColor"));
    Long strokeColorLong = Long.parseLong(strokeColorString);

    polyline.setUserData(params.get("hashCode"));
    polyline.setOutlineColor(outlineColorLong.intValue());
    polyline.setOutlineWidth(((Double) params.get("outlineWidth")).floatValue());
    polyline.setStrokeColor(strokeColorLong.intValue());
    polyline.setStrokeWidth(((Double) params.get("strokeWidth")).floatValue());
    polyline.setGeodesic((boolean) params.get("isGeodesic"));
    polyline.setDashLength(((Double) params.get("dashLength")).floatValue());
    polyline.setDashOffset(((Double) params.get("dashOffset")).floatValue());
    polyline.setGapLength(((Double) params.get("gapLength")).floatValue());

    polylines.add(polyline);
  }

  private void removePolyline(MethodCall call) {
    Map<String, Object> params = ((Map<String, Object>) call.arguments);
    MapObjectCollection mapObjects = mapView.getMap().getMapObjects();
    Iterator<PolylineMapObject> iterator = polylines.iterator();

    while (iterator.hasNext()) {
      PolylineMapObject polylineMapObject = iterator.next();
      if (polylineMapObject.getUserData().equals(params.get("hashCode"))) {
        mapObjects.remove(polylineMapObject);
        iterator.remove();
      }
    }
  }

  private void moveWithParams(Map<String, Object> params, CameraPosition cameraPosition) {
    if (((Boolean) params.get("animate"))) {
      Animation.Type type = ((Boolean) params.get("smoothAnimation")) ? Animation.Type.SMOOTH : Animation.Type.LINEAR;
      Animation animation = new Animation(type, ((Double) params.get("animationDuration")).floatValue());

      mapView.getMap().move(cameraPosition, animation, null);
    } else {
      mapView.getMap().move(cameraPosition);
    }
  }

  private boolean hasLocationPermission() {
    int permissionState = ActivityCompat.checkSelfPermission(mapView.getContext(), Manifest.permission.ACCESS_FINE_LOCATION);
    return permissionState == PackageManager.PERMISSION_GRANTED;
  }

  private void zoomIn() {
    zoom(1f);
  }

  private void zoomOut() {
    zoom(-1f);
  }

  private void zoom(float step) {
    Point zoomPoint = mapView.getMap().getCameraPosition().getTarget();
    float currentZoom = mapView.getMap().getCameraPosition().getZoom();
    float tilt = mapView.getMap().getCameraPosition().getTilt();
    float azimuth = mapView.getMap().getCameraPosition().getAzimuth();
    mapView.getMap().move(
      new CameraPosition(
        zoomPoint,
        currentZoom + step,
        tilt,
        azimuth
      ),
      new Animation(Animation.Type.SMOOTH, 1),
      null);
  }

  private List<RequestPoint> getRouterPoints(MethodCall call) {
    Map<String, Object> params = ((Map<String, Object>) call.arguments);

    final Double srcLatitude = (Double) params.get("srcLatitude");
    final Double srcLongitude = (Double) params.get("srcLongitude");
    final Double destLatitude = (Double) params.get("destLatitude");
    final Double destLongitude = (Double) params.get("destLongitude");

    final List<RequestPoint> points = new ArrayList<>();
    points.add(new RequestPoint(new Point(srcLatitude, srcLongitude), RequestPointType.WAYPOINT, null));
    points.add(new RequestPoint(new Point(destLatitude, destLongitude), RequestPointType.WAYPOINT, null));

    return points;
  }

  private void requestMasstransitRoute(MethodCall call) {
    MasstransitOptions options = new MasstransitOptions(
      new ArrayList<String>(),
      new ArrayList<String>(),
      new TimeOptions()
    );

    if (buildRouteChannel != null) {
      clearRoute();
    }

    masstransitRouter.requestRoutes(getRouterPoints(call), options, this);
  }

  private void requestPedestrianRoute(MethodCall call) {
    if (buildRouteChannel != null) {
      clearRoute();
    }

    pedestrianRouter.requestRoutes(getRouterPoints(call), new TimeOptions(), this);
  }

  private void requestBicycleRoute(MethodCall call) {
    if (buildRouteChannel != null) {
      clearRoute();
    }

    bicycleRouter.requestRoutes(getRouterPoints(call), this);
  }

  private void requestDrivingRoute(MethodCall call) {
    if (buildRouteChannel != null) {
      clearRoute();
    }

    final DrivingOptions options = new DrivingOptions();
    drivingRouter.requestRoutes(getRouterPoints(call), options, this);
  }

  private void search(MethodCall call) {
    final Map<String, Object> params = ((Map<String, Object>) call.arguments);
    final String query = (String) params.get("query");
    final Double userPositionLatitude = (Double) params.get("userPositionLatitude");
    final Double userPositionLongitude = (Double) params.get("userPositionLongitude");

    final SearchOptions options = new SearchOptions();

    if (userPositionLatitude != null && userPositionLongitude != null) {
      final Point userPosition = new Point(userPositionLatitude, userPositionLongitude);
      options.setUserPosition(userPosition);
    }

    if (query != null) {
      searchSession = searchManager.submit(
        query,
        VisibleRegionUtils.toPolygon(mapView.getMap().getVisibleRegion()),
        options,
        this
      );
    }
  }

  private double getDistance(MethodCall call) {
    final Map<String, Object> params = ((Map<String, Object>) call.arguments);
    final Double srcLatitude = (Double) params.get("srcLatitude");
    final Double srcLongitude = (Double) params.get("srcLongitude");
    final Double destLatitude = (Double) params.get("destLatitude");
    final Double destLongitude = (Double) params.get("destLongitude");

    return Geo.distance(
      new Point(srcLatitude, srcLongitude),
      new Point(destLatitude, destLongitude)
    );
  }

  private void clearRoute() {
    final MapObjectCollection mapObjects = mapView.getMap().getMapObjects();

    for (final PolylineMapObject polyline : routePolylines) {
      mapObjects.remove(polyline);
    }

    routePolylines.clear();
  }

  @Override
  public void onMethodCall(MethodCall call, MethodChannel.Result result) {
    estimationRouteChannel = null;
    buildRouteChannel = null;

    switch (call.method) {
      case "showUserLayer":
        showUserLayer(call);
        result.success(null);
        break;
      case "hideUserLayer":
        hideUserLayer();
        result.success(null);
        break;
      case "setMapStyle":
        setMapStyle(call);
        result.success(null);
        break;
      case "move":
        move(call);
        result.success(null);
        break;
      case "setBounds":
        setBounds(call);
        result.success(null);
        break;
      case "addPlacemark":
        addPlacemark(call);
        result.success(null);
        break;
      case "removePlacemark":
        removePlacemark(call);
        result.success(null);
        break;
      case "updatePlacemarkPoint":
        updatePlacemarkPoint(call);
        result.success(null);
        break;
      case "addPolyline":
        addPolyline(call);
        result.success(null);
        break;
      case "removePolyline":
        removePolyline(call);
        result.success(null);
        break;
      case "zoomIn":
        zoomIn();
        result.success(null);
        break;
      case "zoomOut":
        zoomOut();
        result.success(null);
        break;
      case "getTargetPoint":
        Map<String, Object> point = getTargetPoint();
        result.success(point);
        break;
      case "requestMasstransitRoute":
        buildRouteChannel = result;
        requestMasstransitRoute(call);
        break;
      case "requestPedestrianRoute":
        buildRouteChannel = result;
        requestPedestrianRoute(call);
        break;
      case "requestBicycleRoute":
        buildRouteChannel = result;
        requestBicycleRoute(call);
        break;
      case "requestDrivingRoute":
        buildRouteChannel = result;
        requestDrivingRoute(call);
        break;
      case "estimateMasstransitRoute":
        estimationRouteChannel = result;
        requestMasstransitRoute(call);
        break;
      case "estimatePedestrianRoute":
        estimationRouteChannel = result;
        requestPedestrianRoute(call);
        break;
      case "estimateBicycleRoute":
        estimationRouteChannel = result;
        requestBicycleRoute(call);
        break;
      case "estimateDrivingRoute":
        estimationRouteChannel = result;
        requestDrivingRoute(call);
        break;
      case "clearRoutes":
        clearRoute();
        result.success(null);
        break;
      case "search":
        searchChannel = result;
        search(call);
        break;
      case "distance":
        double distance = getDistance(call);
        result.success(distance);
        break;
      default:
        result.notImplemented();
        break;
    }
  }

  @Override
  public void onMasstransitRoutes(@NonNull List<Route> routes) {
    masstransitSectionInfoList.clear();
    masstransitRoutePointsList.clear();

    // In this example we consider first alternative only
    if (routes.size() > 0) {
      if (estimationRouteChannel != null) {
        final String estimation = routes.get(0).getMetadata().getWeight().getTime().getText();
        estimationRouteChannel.success(estimation);
        return;
      } else {
        for (Section section : routes.get(0).getSections()) {
          drawSection(
            section,
            SubpolylineHelper.subpolyline(routes.get(0).getGeometry(), section.getGeometry())
          );
        }
      }
    }

    masstransitSectionInfoList = mergeSectionInfoList(masstransitSectionInfoList);
    masstransitRoutePointsList = createRoutePoints(masstransitSectionInfoList);

    if (buildRouteChannel != null) {
      final Map<String, Object> result = new HashMap<>();
      final List<Map<String, Object>> sections = new ArrayList<>();
      final List<Map<String, Object>> points = new ArrayList<>();

      for (SectionInfo section : masstransitSectionInfoList) {
        sections.add(section.serialize());
      }

      for (RoutePoint point : masstransitRoutePointsList) {
        points.add(point.serialize());
      }

      result.put("sections", sections);
      result.put("points", points);

      buildRouteChannel.success(result);
    }
  }

  private List<RoutePoint> createRoutePoints(List<SectionInfo> sections) {
    final List<RoutePoint> points = new ArrayList<>();

    if (sections.isEmpty()) {
      return points;
    }

    // Add bound point
    points.add(sections.get(0).points.startPoint);
    SectionInfo nextSection;

    for (int i = 0; i < sections.size() - 1; i += 1) {
      final SectionInfo section = sections.get(i);
      nextSection = sections.get(i + 1);

      final RoutePoint p1 = section.points.endPoint;
      final RoutePoint p2 = nextSection.points.startPoint;

      points.add(maxPoint(p1, p2));
    }

    // Add bound point
    points.add(sections.get(sections.size() - 1).points.endPoint);
    return points;
  }

  private List<SectionInfo> mergeSectionInfoList(List<SectionInfo> sections) {
    final List<SectionInfo> optimizedSections = new ArrayList<>();
    SectionInfo prevSection = null;

    for (final SectionInfo section : sections) {
      if (prevSection != null) {
        if (prevSection.tag.equals("pedestrian") && section.tag.equals("pedestrian")) {
          prevSection = new SectionInfo(
            /* tag */ prevSection.tag,
            /* duration */ prevSection.duration + section.duration,
            /* walkingDistance */ prevSection.walkingDistance + section.walkingDistance,
            /* color */ prevSection.color,
            /* points */ mergePoints(prevSection.points, section.points)
          );
        } else {
          optimizedSections.add(prevSection);
          prevSection = section;
        }
      } else {
        prevSection = section;
      }
    }

    if (prevSection != null) {
      optimizedSections.add(prevSection);
    }

    return optimizedSections;
  }

  private PointBound mergePoints(PointBound b1, PointBound b2) {
    return new PointBound(
      /* startPoint */ maxPoint(b1.startPoint, b2.startPoint),
      /* endPoint */ maxPoint(b1.endPoint, b2.endPoint)
    );
  }

  private RoutePoint maxPoint(RoutePoint p1, RoutePoint p2) {
    return p1.zIndex > p2.zIndex ? p1 : p2;
  }

  @Override
  public void onMasstransitRoutesError(@NonNull Error error) {
    Log.e("MasstransitRoutesError", "Error" + error);
    if (buildRouteChannel != null) {
      buildRouteChannel.error("MasstransitRoutesError", error.toString(), error);
    }
  }

  @Override
  public void onBicycleRoutes(@NonNull List<com.yandex.mapkit.transport.bicycle.Route> routes) {
    if (routes.size() > 0) {
      if (estimationRouteChannel != null) {
        final String estimation = routes.get(0).getWeight().getTime().getText();
        estimationRouteChannel.success(estimation);
      } else {
        for (com.yandex.mapkit.transport.bicycle.Section section : routes.get(0).getSections()) {
          drawBicycleSection(
            section,
            SubpolylineHelper.subpolyline(
              routes.get(0).getGeometry(), section.getGeometry()));
        }
      }
    }

    if (buildRouteChannel != null) {
      buildRouteChannel.success(null);
    }
  }

  @Override
  public void onBicycleRoutesError(@NonNull Error error) {
    Log.e("BicycleRoutesError", "Error" + error);
    if (buildRouteChannel != null) {
      buildRouteChannel.error("BicycleRoutesError", error.toString(), error);
    }
  }

  private void drawBicycleSection(
    com.yandex.mapkit.transport.bicycle.Section section,
    Polyline geometry
  ) {
    // Draw a section polyline on a map
    // Set its color depending on the information which the section contains
    MapObjectCollection mapObjects = mapView.getMap().getMapObjects();
    PolylineMapObject polylineMapObject = mapObjects.addPolyline(geometry);
    polylineMapObject.setStrokeColor(0xFFA06ED9);
    routePolylines.add(polylineMapObject);
  }

  private void drawSection(Section section, Polyline geometry) {
    // Draw a section polyline on a map
    // Set its color depending on the information which the section contains
    MapObjectCollection mapObjects = mapView.getMap().getMapObjects();
    PolylineMapObject polylineMapObject = mapObjects.addPolyline(geometry);
    final SectionInfo info = getMasstransitSectionInfo(section);

    polylineMapObject.setStrokeColor(info.color);
    masstransitSectionInfoList.add(info);
    routePolylines.add(polylineMapObject);
  }

  private SectionInfo getMasstransitSectionInfo(Section section) {
    int color = 0xFFA06ED9;
    String tag = "";
    String lineName = "";
    String lineId = "?";
    String directionDesc = "";
    List<String> intermediateStations = new ArrayList<>();
    final SectionMetadata.SectionData data = section.getMetadata().getData();
    int zIndex = 0;

    // Masstransit route section defines exactly one on the following
    // 1. Wait until public transport unit arrives
    // 2. Walk
    // 3. Transfer to a nearby stop (typically transfer to a connected
    //    underground station)
    // 4. Ride on a public transport
    // Check the corresponding object for null to get to know which
    // kind of section it is
    if (data.getTransports() != null) {
      // A ride on a public transport section contains information about
      // all known public transport lines which can be used to travel from
      // the start of the section to the end of the section without transfers
      // along a similar geometry
      for (Transport transport : data.getTransports()) {
        final Line.Style style = transport.getLine().getStyle();
        lineName = transport.getLine().getName();
//        lineId = transport.getTransports().get(0).getThread().getId();
        // Some public transport lines may have a color associated with them
        // Typically this is the case of underground lines
        if (style != null && style.getColor() != null) {
          // The color is in RRGGBB 24-bit format
          // Convert it to AARRGGBB 32-bit format, set alpha to 255 (opaque)
          color = style.getColor() | 0xFF000000;
          break;
        }
      }
      // Let us draw bus lines in green and tramway lines in red
      // Draw any other public transport lines in blue
      HashSet<String> knownVehicleTypes = new HashSet<>();

      knownVehicleTypes.add("bus");
      knownVehicleTypes.add("tramway");
      knownVehicleTypes.add("underground");

      for (Transport transport : data.getTransports()) {
        String sectionVehicleType = getVehicleType(transport, knownVehicleTypes);
        if (sectionVehicleType == null) {
          break;
        } else if (sectionVehicleType.equals("bus")) {
          color = 0xFF33B609;  // Green
          tag = "bus";
          zIndex = 1;
        } else if (sectionVehicleType.equals("tramway")) {
          color = 0xFF33B609;  // Red
          tag = "tramway";
          zIndex = 2;
        } else if (sectionVehicleType.equals("underground")) {
          tag = "underground";
          zIndex = 3;
        }
      }
    } else {
      // This is not a public transport ride section
      // In this example let us draw it in black
      color = 0xFF7073EE;
      tag = "pedestrian";
    }

    final double duration = section.getMetadata().getWeight().getTime().getValue();
    final double walkingDistance = section.getMetadata().getWeight().getWalkingDistance().getValue();
    final RoutePoint startPoint;
    final RoutePoint endPoint;

    if (section.getStops().size() > 0) {
      startPoint = new RoutePoint(
        /* name */ section.getStops().get(0).getStop().getName(),
        /* color */ color,
        /* zIndex */ zIndex
      );

      endPoint = new RoutePoint(
        /* name */ section.getStops().get(section.getStops().size() - 1).getStop().getName(),
        /* color */ color,
        /* zIndex */ zIndex
      );
    } else {
      startPoint = new RoutePoint(
        /* name */ "",
        /* color */ 0,
        /* zIndex */ -1
      );

      endPoint = new RoutePoint(
        /* name */ "",
        /* color */ 0,
        /* zIndex */ -1
      );
    }

    final PointBound points = new PointBound(startPoint, endPoint);

    if (section.getStops().size() > 2) {
      directionDesc = section.getStops().get(1).getStop().getName();

      // Not including first and last stops
      for (int i = 1; i < section.getStops().size() - 1; ++i) {
        final RouteStop stop = section.getStops().get(i);
        intermediateStations.add(stop.getStop().getName());
      }
    }

    if (tag.equals("pedestrian")) {
      return new SectionInfo(
        /* tag */ tag,
        /* duration */ duration,
        /* walkingDistance */ walkingDistance,
        /* color */ color,
        /* points */ points
      );
    } else {
      return new SectionTransport(
        /* tag */ tag,
        /* duration */ duration,
        /* walkingDistance */ walkingDistance,
        /* color */ color,
        /* lineName */ lineName,
        /* lineId */ "?",
        /* directionDesc */ directionDesc,
        /* interval */ "?",
        /* intermediateStation */ intermediateStations,
        /* points */ points
      );
    }
  }

  private String getVehicleType(Transport transport, HashSet<String> knownVehicleTypes) {
    // A public transport line may have a few 'vehicle types' associated with it
    // These vehicle types are sorted from more specific (say, 'histroic_tram')
    // to more common (say, 'tramway').
    // Your application does not know the list of all vehicle types that occur in the data
    // (because this list is expanding over time), therefore to get the vehicle type of
    // a public line you should iterate from the more specific ones to more common ones
    // until you get a vehicle type which you can process
    // Some examples of vehicle types:
    // "bus", "minibus", "trolleybus", "tramway", "underground", "railway"
    for (String type : transport.getLine().getVehicleTypes()) {
      if (knownVehicleTypes.contains(type)) {
        return type;
      }
    }
    return null;
  }

  @Override
  public void onSearchResponse(@NonNull Response response) {
    final List<Map<String, Object>> results = new ArrayList<>();

    for (GeoObjectCollection.Item searchResult : response.getCollection().getChildren()) {
      final Map<String, Object> arguments = new HashMap<>();
      final GeoObject obj = searchResult.getObj();

      if (obj == null) {
        continue;
      }

      if (obj.getGeometry().isEmpty()) {
        continue;
      }

      final Point point = obj.getGeometry().get(0).getPoint();

      if (point == null) {
        continue;
      }

      arguments.put("name", obj.getName());
      arguments.put("description", obj.getDescriptionText());
      arguments.put("latitude", point.getLatitude());
      arguments.put("longitude", point.getLongitude());

      results.add(arguments);
    }

    if (searchChannel != null) {
      searchChannel.success(results);
    }
  }

  @Override
  public void onSearchError(@NonNull Error error) {
    Log.e("SearchError", "Error" + error);
    if (searchChannel != null) {
      searchChannel.error("SearchError", error.toString(), error);
    }
  }

  @Override
  public void onDrivingRoutes(@NonNull List<DrivingRoute> routes) {
    if (routes.size() > 0) {
      if (estimationRouteChannel != null) {
        final String estimation = routes.get(0).getMetadata().getWeight().getTime().getText();
        estimationRouteChannel.success(estimation);
      } else {
        final DrivingRoute route = routes.get(0);

        MapObjectCollection mapObjects = mapView.getMap().getMapObjects();
        PolylineMapObject polylineMapObject = mapObjects.addPolyline(route.getGeometry());
        polylineMapObject.setStrokeColor(0xFFA06ED9);
        routePolylines.add(polylineMapObject);
      }
    }

    if (buildRouteChannel != null) {
      buildRouteChannel.success(null);
    }
  }

  @Override
  public void onDrivingRoutesError(@NonNull Error error) {
    Log.e("DrivingRoutesError", "Error" + error);
    if (searchChannel != null) {
      searchChannel.error("DrivingRoutesError", error.toString(), error);
    }
  }

  private class YandexUserLocationObjectListener implements UserLocationObjectListener {
    private PluginRegistry.Registrar pluginRegistrar;

    private YandexUserLocationObjectListener(PluginRegistry.Registrar pluginRegistrar) {
      this.pluginRegistrar = pluginRegistrar;
    }

    public void onObjectAdded(UserLocationView userLocationView) {
      final ImageProvider arrowIconProvider = ImageProvider.fromAsset(
        mapView.getContext(),
        pluginRegistrar.lookupKeyForAsset(userLocationArrowIconName)
      );

      final ImageProvider pinIconProvider = ImageProvider.fromAsset(
        mapView.getContext(),
        pluginRegistrar.lookupKeyForAsset(userLocationPinIconName)
      );

      userLocationView.getArrow().setIcon(arrowIconProvider);
      userLocationView.getPin().setIcon(pinIconProvider);
    }

    public void onObjectRemoved(UserLocationView view) {
    }

    public void onObjectUpdated(UserLocationView view, ObjectEvent event) {
      view.getArrow().getGeometry();
    }
  }

  private class YandexMapObjectTapListener implements MapObjectTapListener {
    public boolean onMapObjectTap(MapObject mapObject, Point point) {
      Map<String, Object> arguments = new HashMap<>();
      arguments.put("hashCode", mapObject.getUserData());
      arguments.put("latitude", point.getLatitude());
      arguments.put("longitude", point.getLongitude());

      methodChannel.invokeMethod("onMapObjectTap", arguments);

      return true;
    }
  }

  @Override
  public void onInputConnectionLocked() {
  }

  @Override
  public void onInputConnectionUnlocked() {
  }
}
