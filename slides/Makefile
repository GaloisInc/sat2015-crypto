PANDOC_OPTS=-t beamer --latex-engine=xelatex -V theme:m

all: talk.pdf

%.pdf: %.md header.tex
	pandoc -H header.tex ${PANDOC_OPTS} -o $@ $<
