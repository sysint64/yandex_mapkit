import 'package:flutter/foundation.dart';

class SearchSuggestion {
  SearchSuggestion({@required this.name, @required this.description});

  final String name;
  final String description;

  @override
  String toString() {
    return '("$name", "$description")';
  }
}