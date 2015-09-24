all: talk.pdf

%.pdf: %.tex %.bib
	latexmk -xelatex -bibtex $<
