FROM openjdk:14-slim
RUN apt-get update
RUN apt-get install -y make

RUN echo $PWD
RUN mkdir siftagent
WORKDIR siftagent
COPY ./Makefile .
COPY ./ginrummy ./ginrummy/
COPY ./sigra ./sigra/

RUN make
ENTRYPOINT ["make", "run"]