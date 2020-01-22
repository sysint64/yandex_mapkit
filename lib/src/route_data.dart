import 'package:flutter/foundation.dart';

class RouteInfo {
  final List<SectionInfo> sections;
  final List<RoutePoint> points;

  RouteInfo(this.sections, this.points);
}

class RoutePoint {
  final String name;
  final int color;
  final int zIndex;

  RoutePoint({
    @required this.name,
    @required this.color,
    @required this.zIndex,
  });
}

class SectionInfo {
  final String tag;
  final double duration;
  final double walkingDistance;
  final int color;
  final RoutePoint startPoint;
  final RoutePoint endPoint;

  SectionInfo({
    @required this.tag,
    @required this.duration,
    @required this.walkingDistance,
    @required this.color,
    @required this.startPoint,
    @required this.endPoint,
  });
}

class SectionTransport extends SectionInfo {
  final String lineName;
  final String lineId;
  final String directionDesc;
  final String interval;
  final int intermediateStationsSize;

  SectionTransport({
    @required String tag,
    @required double duration,
    @required double walkingDistance,
    @required int color,
    @required RoutePoint startPoint,
    @required RoutePoint endPoint,
    @required this.lineName,
    @required this.lineId,
    @required this.directionDesc,
    @required this.interval,
    @required this.intermediateStationsSize,
  }) : super(
      tag: tag,
      duration: duration,
      walkingDistance: walkingDistance,
      color: color,
      startPoint: startPoint,
      endPoint: endPoint,
  );
}

SectionInfo createSectionInfoFromMap(dynamic data) {
  if (data['tag'] != 'pedestrian') {
    return SectionTransport(
      tag: data['tag'],
      duration: data['duration'],
      walkingDistance: data['walkingDistance'],
      color: data['color'],
      startPoint: createRoutePointFromMap(data['points.startPoint']),
      endPoint: createRoutePointFromMap(data['points.endPoint']),
      lineName: data['lineName'],
      lineId: data['lineId'],
      directionDesc: data['directionDesc'],
      interval: data['interval'],
      intermediateStationsSize: data['intermediateStations.size'],
    );
  } else {
    return SectionInfo(
        tag: data['tag'],
        duration: data['duration'],
        walkingDistance: data['walkingDistance'],
        color: data['color'],
        startPoint: createRoutePointFromMap(data['points.startPoint']),
        endPoint: createRoutePointFromMap(data['points.endPoint']),
    );
  }
}

RoutePoint createRoutePointFromMap(dynamic data) {
  return RoutePoint(
      name: data['name'],
      color: data['color'],
      zIndex: data['zIndex'],
  );
}