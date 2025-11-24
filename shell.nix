{ pkgs ? import <nixpkgs> {} }:

pkgs.mkShell {
    buildInputs = [
        pkgs.openjdk21
        pkgs.maven
    ];

    shellHook = ''
        echo "Ambiente Java (OpenJDK 21) e Maven prontos para uso."
    '';
}
