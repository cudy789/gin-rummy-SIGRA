FROM openjdk:14-slim
RUN apt-get update
RUN apt-get install -y make

RUN mkdir siftagent
WORKDIR siftagent
COPY ./Makefile .
COPY ./ginrummy ./ginrummy/
COPY ./siftagent ./siftagent/

RUN make
ENTRYPOINT ["make", "run"]