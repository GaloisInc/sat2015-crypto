LSS?=lss

%.log: %.saw
	${SAW} $< 2>&1 | tee $@

aes.aig: aes.bc
	${LSS} $<
