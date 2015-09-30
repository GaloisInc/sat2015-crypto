LSS?=lss
SAW?=saw

%.bc: %.c
	clang -emit-llvm -c $< -o $@

%.log: %.saw
	${SAW} $< 2>&1 | tee $@

aes.aig: aes.bc
	${LSS} $<
