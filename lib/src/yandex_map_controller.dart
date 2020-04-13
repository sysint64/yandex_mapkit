import 'dart:async';
import 'dart:io' show Platform;
import 'dart:math';

import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter/widgets.dart';
import 'package:yandex_mapkit/src/geo_object.dart';
import 'package:yandex_mapkit/src/route_data.dart';
import 'package:yandex_mapkit/src/search_suggestion.dart';

import 'map_animation.dart';
import 'placemark.dart';
import 'point.dart';
import 'polyline.dart';

class YandexMapController extends ChangeNotifier {
  YandexMapController._(
    MethodChannel channel,
    this.onGeoObjectTap,
  ) : _channel = channel {
    _channel.setMethodCallHandler(_handleMethodCall);
  }

  static const double kTilt = 0.0;
  static const double kAzimuth = 0.0;
  static const double kZoom = 15.0;

  final MethodChannel _channel;

  final List<Placemark> placemarks = <Placemark>[];
  final List<Polyline> polylines = <Polyline>[];
  Function(GeoObject) onGeoObjectTap;

  static YandexMapController init(
    int id,
    Function(GeoObject) onGeoObjectTap,
  ) {
    final MethodChannel methodChannel =
        MethodChannel('yandex_mapkit/yandex_map_$id');

    return YandexMapController._(methodChannel, onGeoObjectTap);
  }

  /// Shows an icon at current user location
  ///
  /// Requires location permissions:
  ///
  /// `NSLocationWhenInUseUsageDescription`
  ///
  /// `android.permission.ACCESS_FINE_LOCATION`
  ///
  /// Does nothing if these permissions where denied
  Future<void> showUserLayer({
    @required String arrowIconName,
    @required String pinIconName,
  }) async {
    // (C) Ant, workaround to correctly run on iOS due to lack of 2 icons in this lib
    if (Platform.isIOS) {
      await _channel.invokeMethod<void>('showUserLayer', <String, dynamic>{
        'iconName': arrowIconName,
      });
    } else {
      await _channel.invokeMethod<void>('showUserLayer', <String, dynamic>{
        'arrowIconName': arrowIconName,
        'pinIconName': pinIconName,
      });
    }
  }

  /// Hides an icon at current user location
  ///
  /// Requires location permissions:
  ///
  /// `NSLocationWhenInUseUsageDescription`
  ///
  /// `android.permission.ACCESS_FINE_LOCATION`
  ///
  /// Does nothing if these permissions where denied
  Future<void> hideUserLayer() async {
    await _channel.invokeMethod<void>('hideUserLayer');
  }

  /// Applies styling to the map
  Future<void> setMapStyle({@required String style}) async {
    await _channel
        .invokeMethod<void>('setMapStyle', <String, dynamic>{'style': style});
  }

  List<Map<String, dynamic>> _pointsSerialize(Iterable<Point> points) {
    return points
        .map(
          (Point it) => <String, dynamic>{
            'latitude': it.latitude,
            'longitude': it.longitude,
          },
        )
        .toList();
  }

  Future<RouteInfo> requestMasstransitRoute(Iterable<Point> points) async {
    final dynamic data = await _channel.invokeMethod<dynamic>(
      'requestMasstransitRoute',
      _pointsSerialize(points),
    );

    final List<dynamic> sectionsResponse = data['sections'];
    final List<dynamic> pointsResponse = data['points'];

    final List<SectionInfo> routeSections = sectionsResponse
        .map((dynamic it) => createSectionInfoFromMap(it))
        .toList();

    final List<RoutePoint> routePoints = pointsResponse
        .map((dynamic it) => createRoutePointFromMap(it))
        .toList();

    return RouteInfo(routeSections, routePoints);
  }

  Future<void> requestPedestrianRoute(Iterable<Point> points) async {
    await _channel.invokeMethod<void>(
      'requestPedestrianRoute',
      _pointsSerialize(points),
    );
  }

  Future<void> requestBicycleRoute(Iterable<Point> points) async {
    await _channel.invokeMethod<void>(
      'requestBicycleRoute',
      _pointsSerialize(points),
    );
  }

  Future<void> requestDrivingRoute(Iterable<Point> points) async {
    await _channel.invokeMethod<void>(
      'requestDrivingRoute',
      _pointsSerialize(points),
    );
  }

  Future<String> estimateMasstransitRoute(Iterable<Point> points) async {
    return _channel.invokeMethod<String>(
      'estimateMasstransitRoute',
      _pointsSerialize(points),
    );
  }

  Future<String> estimatePedestrianRoute(Iterable<Point> points) async {
    return _channel.invokeMethod<String>(
      'estimatePedestrianRoute',
      _pointsSerialize(points),
    );
  }

  Future<String> estimateBicycleRoute(Iterable<Point> points) async {
    return _channel.invokeMethod<String>(
      'estimateBicycleRoute',
      _pointsSerialize(points),
    );
  }

  Future<String> estimateDrivingRoute(Iterable<Point> points) async {
    return _channel.invokeMethod<String>(
      'estimateDrivingRoute',
      _pointsSerialize(points),
    );
  }

  Future<void> clearRoutes() async {
    await _channel.invokeMethod<void>('clearRoutes');
  }

  Future<void> clearAll() async {
    placemarks.clear();
    polylines.clear();
    await _channel.invokeMethod<void>('clearAll');
  }

  Future<void> move(
      {@required Point point,
      double zoom = kZoom,
      double azimuth = kAzimuth,
      double tilt = kTilt,
      MapAnimation animation}) async {
    await _channel.invokeMethod<void>('move', <String, dynamic>{
      'latitude': point.latitude,
      'longitude': point.longitude,
      'zoom': zoom,
      'azimuth': azimuth,
      'tilt': tilt,
      'animate': animation != null,
      'smoothAnimation': animation?.smooth,
      'animationDuration': animation?.duration
    });
  }

  Future<void> setBounds(
      {@required Point southWestPoint,
      @required Point northEastPoint,
      MapAnimation animation}) async {
    await _channel.invokeMethod<void>('setBounds', <String, dynamic>{
      'southWestLatitude': southWestPoint.latitude,
      'southWestLongitude': southWestPoint.longitude,
      'northEastLatitude': northEastPoint.latitude,
      'northEastLongitude': northEastPoint.longitude,
      'animate': animation != null,
      'smoothAnimation': animation?.smooth,
      'animationDuration': animation?.duration
    });
  }

  Future<void> setFocusRect(
      {@required double topLeft,
      @required double topRight,
      @required double bottomLeft,
      @required double bottomRight}) async {
    await _channel.invokeMethod<void>('setFocusRect', <String, dynamic>{
      'topLeft': topLeft,
      'topRight': topRight,
      'bottomLeft': bottomLeft,
      'bottomRight': bottomRight,
    });
  }

  /// Does nothing if passed `Placemark` is `null`
  Future<void> addPlacemark(Placemark placemark) async {
    if (placemark != null) {
      await _channel.invokeMethod<void>(
          'addPlacemark', _placemarkParams(placemark));
      placemarks.add(placemark);
    }
  }

  // Does nothing if passed `Placemark` wasn't added before
  Future<void> removePlacemark(Placemark placemark) async {
    if (placemarks.remove(placemark)) {
      await _channel.invokeMethod<void>(
          'removePlacemark', <String, dynamic>{'hashCode': placemark.hashCode});
    }
  }

  Future<void> updatePlacemarkPoint(Placemark placemark, Point point) async {
    await _channel.invokeMethod<void>('updatePlacemarkPoint', <String, dynamic>{
      'hashCode': placemark.hashCode,
      'latitude': point.latitude,
      'longitude': point.longitude,
    });
  }

  Future<double> getDistance(Point src, Point dest) async {
    return await _channel.invokeMethod<double>('distance', <String, dynamic>{
      'srcLatitude': src.latitude,
      'srcLongitude': src.longitude,
      'destLatitude': dest.latitude,
      'destLongitude': dest.longitude,
    });
  }

  /// Does nothing if passed `Polyline` is `null`
  Future<void> addPolyline(Polyline polyline) async {
    if (polyline != null) {
      await _channel.invokeMethod<void>(
          'addPolyline', _polylineParams(polyline));
      polylines.add(polyline);
    }
  }

  /// Does nothing if passed `Polyline` wasn't added before
  Future<void> removePolyline(Polyline polyline) async {
    if (polylines.remove(polyline)) {
      await _channel.invokeMethod<void>(
          'removePolyline', <String, dynamic>{'hashCode': polyline.hashCode});
    }
  }

  Future<void> zoomIn() async {
    await _channel.invokeMethod<void>('zoomIn');
  }

  Future<void> zoomOut() async {
    await _channel.invokeMethod<void>('zoomOut');
  }

  Future<Point> getTargetPoint() async {
    final dynamic point =
        await _channel.invokeMethod<dynamic>('getTargetPoint');
    return Point(latitude: point['latitude'], longitude: point['longitude']);
  }

  Future<List<SearchSuggestion>> search(String key) async {
    final List<dynamic> data = await _channel.invokeMethod<List<dynamic>>(
      'search',
      <String, dynamic>{'query': key},
    );

    final List<SearchSuggestion> result = <SearchSuggestion>[];

    for (final dynamic item in data) {
      result.add(
        SearchSuggestion(
          name: item['name'],
          description: item['description'],
          latitude: item['latitude'],
          longitude: item['longitude'],
        ),
      );
    }

    return result;
  }

  Future<void> _handleMethodCall(MethodCall call) async {
    switch (call.method) {
      case 'onMapObjectTap':
        _onMapObjectTap(call.arguments);
        break;
      case 'onGeoObjectTap':
        _onGeoObjectTap(call.arguments);
        break;

      default:
        throw MissingPluginException();
    }
  }

  void _onMapObjectTap(dynamic arguments) {
    final int hashCode = arguments['hashCode'];
    final double latitude = arguments['latitude'];
    final double longitude = arguments['longitude'];

    final Placemark placemark = placemarks.firstWhere(
        (Placemark placemark) => placemark.hashCode == hashCode,
        orElse: () => null);

    if (placemark != null) {
      placemark.onTap(latitude, longitude);
    }

    print('TAP HASH CODE: $hashCode');
  }

  void _onGeoObjectTap(dynamic arguments) {
    final String name = arguments['name'];
    final String description = arguments['description'];
    final double latitude = arguments['latitude'];
    final double longitude = arguments['longitude'];

    final GeoObject geoObject = GeoObject(
      name: name,
      description: description,
      point: Point(
        latitude: latitude,
        longitude: longitude,
      ),
    );

    if (onGeoObjectTap != null) {
      onGeoObjectTap(geoObject);
    }
  }

  Map<String, dynamic> _placemarkParams(Placemark placemark) {
    return <String, dynamic>{
      'latitude': placemark.point.latitude,
      'longitude': placemark.point.longitude,
      'opacity': placemark.opacity,
      'isDraggable': placemark.isDraggable,
      'iconName': placemark.iconName,
      'rawImageData': placemark.rawImageData,
      'hashCode': placemark.hashCode
    };
  }

  Map<String, dynamic> _polylineParams(Polyline polyline) {
    final List<Map<String, double>> coordinates = polyline.coordinates
        .map((Point p) => {'latitude': p.latitude, 'longitude': p.longitude})
        .toList();
    return <String, dynamic>{
      'coordinates': coordinates,
      'strokeColor': polyline.strokeColor.value,
      'strokeWidth': polyline.strokeWidth,
      'outlineColor': polyline.outlineColor.value,
      'outlineWidth': polyline.outlineWidth,
      'isGeodesic': polyline.isGeodesic,
      'dashLength': polyline.dashLength,
      'dashOffset': polyline.dashOffset,
      'gapLength': polyline.gapLength,
      'hashCode': polyline.hashCode
    };
  }
}
