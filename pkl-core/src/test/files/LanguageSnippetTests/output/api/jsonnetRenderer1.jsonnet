{
  int: 123,
  float: 1.23,
  bool: true,
  string: 'Pigeon',
  unicodeString: 'abc😀abc😎abc',
  multiLineString: |||
    have a
    great
    day
  |||,
  typedObject: {
    name: 'Bob',
    age: 42,
    address: {
      street: 'Abby Rd.',
    },
  },
  dynamicObject: {
    name: 'Pigeon',
    age: 30,
    address: {
      street: 'Folsom St.',
    },
  },
  annoyingNames: {
    '5hello': 123,
    'local': 'remote',
    'foo.bar': 'baz',
    "single'quote": 'double"quote',
  },
  list: [
    1,
    2,
    3,
    null,
  ],
  someExternalVariable: std.extVar('MY_VARIABLE'),
  someImportStr: importstr 'my/private/key.pem',
}
