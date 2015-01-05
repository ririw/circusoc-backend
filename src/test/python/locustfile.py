import random

from locust import HttpLocust, TaskSet


def login(self):
   pass

def performers(l):
   l.client.get("/performers")

def performer(l):
   id = random.randint(1, 10)
   l.client.get("/performer/%d" % id)

class UserBehavior(TaskSet):
   tasks = {performers:1, performer:10}

   def on_start(self):
       login(self)

class WebsiteUser(HttpLocust):
   host = "http://localhost:8080"
   task_set = UserBehavior
   min_wait=1
   max_wait=1000
