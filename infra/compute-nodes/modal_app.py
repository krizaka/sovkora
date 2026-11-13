# infra/compute-nodes/modal_app.py
# Serverless GPU LLM inference wrapper running on Modal labs

import modal
from typing import Dict

# Define the container image with required python packages
image = (
    modal.Image.debian_slim(python_version="3.10")
    .pip_install(
        "fastapi",
        "uvicorn",
        "transformers",
        "torch",
        "accelerate"
    )
)

app = modal.App(name="orazaka-inference", image=image)

@app.cls(gpu="any", secrets=[modal.Secret.from_name("orazaka-inference-secrets")])
class ModelServer:
    @modal.enter()
    def load_model(self):
        # Cache and load target model weights on container startup
        import torch
        from transformers import AutoTokenizer, AutoModelForCausalLM
        
        self.model_name = "microsoft/Phi-3-mini-4k-instruct"
        self.tokenizer = AutoTokenizer.from_pretrained(self.model_name)
        self.model = AutoModelForCausalLM.from_pretrained(
            self.model_name, 
            device_map="auto", 
            torch_dtype=torch.float16
        )

    @modal.method()
    def generate(self, prompt: str, max_tokens: int = 512) -> str:
        # Execution method called by the Sovkora Java orchestrator
        inputs = self.tokenizer(prompt, return_tensors="pt").to("cuda")
        outputs = self.model.generate(**inputs, max_new_tokens=max_tokens)
        return self.tokenizer.decode(outputs[0], skip_special_tokens=True)

# Expose a simple ASGI web endpoint for remote HTTP calls
@app.function()
@modal.web_endpoint(method="POST")
def run(payload: Dict) -> Dict:
    server = ModelServer()
    prompt = payload.get("prompt", "")
    response = server.generate.remote(prompt)
    return {"response": response}
