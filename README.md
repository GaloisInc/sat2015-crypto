This repository contains slides and examples to accompany the September 25th
invited talk on analyzing cryptographic algorithms with SAT solvers at SAT 2015.

The directory `slides` contains the LaTeX source and PDF version of the slides
for the talk (including a bibliography).

The directory `examples` contains the examples mentioned in the talk. To run the
examples, you'll need the Cryptol interpreter and the SAW tool chain. The
examples depend on features not present in the latest binary release of SAW, so
you'll have to build it from source. Here's how to do that:

* Ensure that you have the Haskell `stack` tool installed. It's available from

    https://github.com/commercialhaskell/stack

* Clone the SAW repository from GitHub

    git clone https://github.com/galoisinc/saw-script

* Set up the `stack configuration`

    ln -s stack.ghc-VERSION-PLATFORM.yaml stack.yaml

where `VERSION` is the GHC version you want to use (choose 7.10 unless you have
a reason not to), and `PLATFORM` is `unix` for most Unix-like systems (including
Linux and OS X), and `windows` for Windows.

* Run the build script

    ./build-sandbox.sh -p

* Put the `saw` binary in your `PATH`. It'll be located in the following
  subdirectory of the directory where you cloned the repository:

    .stack-work/install/ARCH/STACKAGE-VER/GHC-VER/bin/

Once you've done this, you should be able to go into the `examples` directory
and run `make EXAMPLE.log` for each SAWScript file with the name `EXAMPLE.saw`.
The log files will include a description of the theorem proved and the time
taken to complete the proof.

The files named `EXAMPLE.cry` include the Cryptol implementations of the
cryptographic algorithms used for each example.
