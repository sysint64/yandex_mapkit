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

import com.yandex.mapkit.Animation;
import com.yandex.mapkit.GeoObject;
import com.yandex.mapkit.MapKitFactory;
import com.yandex.mapkit.RequestPoint;
import com.yandex.mapkit.RequestPointType;
import com.yandex.mapkit.geometry.BoundingBox;
import com.yandex.mapkit.geometry.Point;
import com.yandex.mapkit.geometry.Polyline;
import com.yandex.mapkit.geometry.SubpolylineHelper;
import com.yandex.mapkit.layers.GeoObjectTapEvent;
import com.yandex.mapkit.layers.GeoObjectTapListener;
import com.yandex.mapkit.layers.ObjectEvent;
import com.yandex.mapkit.map.CameraPosition;
import com.yandex.mapkit.map.MapObject;
import com.yandex.mapkit.map.MapObjectCollection;
import com.yandex.mapkit.map.MapObjectTapListener;
import com.yandex.mapkit.map.PlacemarkMapObject;
import com.yandex.mapkit.map.PolylineMapObject;
import com.yandex.mapkit.mapview.MapView;
import com.yandex.mapkit.transport.TransportFactory;
import com.yandex.mapkit.transport.masstransit.Line;
import com.yandex.mapkit.transport.masstransit.MasstransitOptions;
import com.yandex.mapkit.transport.masstransit.MasstransitRouter;
import com.yandex.mapkit.transport.masstransit.Route;
import com.yandex.mapkit.transport.masstransit.Section;
import com.yandex.mapkit.transport.masstransit.SectionMetadata;
import com.yandex.mapkit.transport.masstransit.Session;
import com.yandex.mapkit.transport.masstransit.TimeOptions;
import com.yandex.mapkit.transport.masstransit.Transport;
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


public class YandexMapController implements PlatformView, MethodChannel.MethodCallHandler, Session.RouteListener {
  private final MapView mapView;
  private final MethodChannel methodChannel;
  private final PluginRegistry.Registrar pluginRegistrar;
  private YandexUserLocationObjectListener yandexUserLocationObjectListener;
  private YandexMapObjectTapListener yandexMapObjectTapListener;
  private UserLocationLayer userLocationLayer;
  private List<PlacemarkMapObject> placemarks = new ArrayList<>();
  private List<PolylineMapObject> polylines = new ArrayList<>();
  private String userLocationIconName;
  private MasstransitRouter router;
  private final List<PolylineMapObject> routePolylines = new ArrayList<>();
  private GeoObjectTapListener geoObjectTapListener;
  private MethodChannel.Result buildRouteChannel;

  public YandexMapController(int id, Context context, PluginRegistry.Registrar registrar) {
    MapKitFactory.initialize(context);
    TransportFactory.initialize(context);

    mapView = new MapView(context);
    MapKitFactory.getInstance().onStart();
    mapView.onStart();
    pluginRegistrar = registrar;
    yandexMapObjectTapListener = new YandexMapObjectTapListener();
    userLocationLayer = MapKitFactory.getInstance().createUserLocationLayer(mapView.getMapWindow());
    yandexUserLocationObjectListener = new YandexUserLocationObjectListener(registrar);

//    mapView.getMap().
    methodChannel = new MethodChannel(registrar.messenger(), "yandex_mapkit/yandex_map_" + id);
    methodChannel.setMethodCallHandler(this);

    router = TransportFactory.getInstance().createMasstransitRouter();
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
    userLocationIconName = (String) params.get("iconName");

    userLocationLayer.setVisible(true);
    userLocationLayer.setHeadingEnabled(true);
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

  private void requestRoute(MethodCall call) {
    MasstransitOptions options = new MasstransitOptions(
      new ArrayList<String>(),
      new ArrayList<String>(),
      new TimeOptions());

    Map<String, Object> params = ((Map<String, Object>) call.arguments);

    final Double srcLatitude = (Double) params.get("srcLatitude");
    final Double srcLongitude = (Double) params.get("srcLongitude");
    final Double destLatitude = (Double) params.get("destLatitude");
    final Double destLongitude = (Double) params.get("destLongitude");

    final List<RequestPoint> points = new ArrayList<>();
    points.add(new RequestPoint(new Point(srcLatitude, srcLongitude), RequestPointType.WAYPOINT, null));
    points.add(new RequestPoint(new Point(destLatitude, destLongitude), RequestPointType.WAYPOINT, null));
    clearRoute();
    router.requestRoutes(points, options, this);
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
      case "requestRoute":
        buildRouteChannel = result;
        requestRoute(call);
        break;
      case "clearRoute":
        clearRoute();
        result.success(null);
        break;
      default:
        result.notImplemented();
        break;
    }
  }

  @Override
  public void onMasstransitRoutes(@NonNull List<Route> routes) {
    // In this example we consider first alternative only
    if (routes.size() > 0) {
      for (Section section : routes.get(0).getSections()) {
        drawSection(
          section.getMetadata().getData(),
          SubpolylineHelper.subpolyline(
            routes.get(0).getGeometry(), section.getGeometry()));
      }
    }

    if (buildRouteChannel != null) {
      buildRouteChannel.success(null);
    }
  }

  @Override
  public void onMasstransitRoutesError(@NonNull Error error) {
    Log.e("MasstransitRoutesError", "Error" + error);
    if (buildRouteChannel != null) {
      buildRouteChannel.error("MasstransitRoutesError", error.toString(), error);
    }
  }


  private void drawSection(SectionMetadata.SectionData data,
                           Polyline geometry) {
    // Draw a section polyline on a map
    // Set its color depending on the information which the section contains
    MapObjectCollection mapObjects = mapView.getMap().getMapObjects();
    PolylineMapObject polylineMapObject = mapObjects.addPolyline(geometry);
    routePolylines.add(polylineMapObject);

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
        // Some public transport lines may have a color associated with them
        // Typically this is the case of underground lines
        if (style != null && style.getColor() != null) {
          polylineMapObject.setStrokeColor(
            // The color is in RRGGBB 24-bit format
            // Convert it to AARRGGBB 32-bit format, set alpha to 255 (opaque)
            style.getColor() | 0xFF000000
          );
          return;
        }
      }
      // Let us draw bus lines in green and tramway lines in red
      // Draw any other public transport lines in blue
      HashSet<String> knownVehicleTypes = new HashSet<>();
      knownVehicleTypes.add("bus");
      knownVehicleTypes.add("tramway");
      for (Transport transport : data.getTransports()) {
        String sectionVehicleType = getVehicleType(transport, knownVehicleTypes);
        if (sectionVehicleType == null) {
          return;
        } else if (sectionVehicleType.equals("bus")) {
          polylineMapObject.setStrokeColor(0xFF00FF00);  // Green
          return;
        } else if (sectionVehicleType.equals("tramway")) {
          polylineMapObject.setStrokeColor(0xFFFF0000);  // Red
          return;
        }
      }
      polylineMapObject.setStrokeColor(0xFF0000FF);  // Blue
    } else {
      // This is not a public transport ride section
      // In this example let us draw it in black
      polylineMapObject.setStrokeColor(0xFF000000);  // Black
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

  private class YandexUserLocationObjectListener implements UserLocationObjectListener {
    private PluginRegistry.Registrar pluginRegistrar;

    private YandexUserLocationObjectListener(PluginRegistry.Registrar pluginRegistrar) {
      this.pluginRegistrar = pluginRegistrar;
    }

    public void onObjectAdded(UserLocationView view) {
      view.getPin().setIcon(
        ImageProvider.fromAsset(
          pluginRegistrar.activity(),
          pluginRegistrar.lookupKeyForAsset(userLocationIconName)
        )
      );
    }

    public void onObjectRemoved(UserLocationView view) {
    }

    public void onObjectUpdated(UserLocationView view, ObjectEvent event) {
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
