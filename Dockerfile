FROM ubuntu:latest
LABEL authors="bishnu"

ENTRYPOINT ["top", "-b"]