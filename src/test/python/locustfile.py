from locust import HttpLocust, TaskSet


def login(self):
   pass

def hello(l):
   l.client.get("/hello")

class UserBehavior(TaskSet):
   tasks = {hello:2}

   def on_start(self):
       login(self)

class WebsiteUser(HttpLocust):
   host = "http://localhost:8080"
   task_set = UserBehavior
   min_wait=1
   max_wait=1000
