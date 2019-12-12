import 'package:flutter/foundation.dart';
import 'package:yandex_mapkit/yandex_mapkit.dart';

class GeoObject {
  GeoObject({
    @required this.name,
    @required this.description,
    @required this.point,
  });

  final String name;
  final String description;
  final Point point;
}
