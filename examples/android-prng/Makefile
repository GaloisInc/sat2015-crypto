JSS?=jss
JSS_OPTS=-j galois.jar

all: jellybean_prng.aig kitkat_prng.aig

%.class: %.java
	javac -g $<

jellybean_prng.aig: SHA1PRNG_SecureRandomImpl_JellyBean.class
	${JSS} ${JSS_OPTS} $(basename $<)

kitkat_prng.aig: SHA1PRNG_SecureRandomImpl_KitKat.class
	${JSS} ${JSS_OPTS} $(basename $<)
