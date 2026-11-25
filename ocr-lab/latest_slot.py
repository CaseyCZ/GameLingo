"""One pending item, no accumulating queue."""
from threading import Condition

class LatestSlot:
    def __init__(self):
        self.condition=Condition()
        self.item=None
        self.closed=False
    def put(self,item):
        with self.condition:
            if self.closed:return False
            replaced=self.item is not None
            self.item=item
            self.condition.notify()
            return replaced
    def clear(self):
        with self.condition:
            had=self.item is not None
            self.item=None
            return had
    def take(self):
        with self.condition:
            self.condition.wait_for(lambda:self.closed or self.item is not None)
            if self.closed:return None
            item,self.item=self.item,None
            return item
    def close(self):
        with self.condition:
            self.closed=True
            self.item=None
            self.condition.notify_all()
