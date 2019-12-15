import 'dart:async';
import 'dart:convert';

import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter/widgets.dart';
import 'package:yandex_mapkit/src/geo_object.dart';

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
  Future<void> showUserLayer({@required String iconName}) async {
    await _channel.invokeMethod<void>(
        'showUserLayer', <String, dynamic>{'iconName': iconName});
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

  Future<void> requestRoute({
    @required Point src,
    @required Point dest,
  }) async {
    await _channel.invokeMethod<void>('requestRoute', <String, dynamic>{
      'srcLatitude': src.latitude,
      'srcLongitude': src.longitude,
      'destLatitude': dest.latitude,
      'destLongitude': dest.longitude,
    });
  }

  Future<void> clearRoutes() async {
    await _channel.invokeMethod<void>('clearRoutes');
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
