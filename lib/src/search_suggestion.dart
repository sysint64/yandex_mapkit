import 'package:flutter/foundation.dart';

class SearchSuggestion {
  SearchSuggestion({
    this.name,
    this.description,
    @required this.latitude,
    @required this.longitude,
  })  : assert(latitude != null),
        assert(longitude != null);

  final String name;
  final String description;
  final double latitude;
  final double longitude;

  @override
  String toString() {
    return '("$name", "$description", $latitude:$longitude)';
  }
}
