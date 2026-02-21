# For running BusPirate-BPIO2-flatbuffer-interface/python samples
{ pkgs ? import <nixpkgs> {} }:

pkgs.mkShell {
  packages = [
    (pkgs.python3.withPackages (ps: [
      ps.pyserial
      ps.cobs
      ps.flatbuffers
    ]))
  ];
}
