PANDOC_OPTS=-t beamer --latex-engine=xelatex -V theme:m \
  --filter pandoc-citeproc --biblio talk.bib --csl=acm.csl -H header.tex

all: talk.pdf

%.pdf: %.md header.tex
	pandoc ${PANDOC_OPTS} -o $@ $<
